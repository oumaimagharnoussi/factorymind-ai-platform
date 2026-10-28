"""Turns a window of readings from one sensor into the numbers the model looks at.

Training and the API both call `sensor_features`, so the model is always fed exactly what it was trained on.
Every feature is divided by the sensor's critical limit: a bearing at 70 degrees and a press at 180 bar are
compared on the same scale ("how close to its own limit"), and one model serves every sensor type.
"""

from dataclasses import dataclass

import numpy as np

# The model answers "will this sensor reach its critical limit within the next HORIZON_HOURS?"
HORIZON_HOURS = 24.0
# Beyond a week the projection says nothing useful; it is capped so a flat line does not mean "infinity".
MAX_PROJECTION_HOURS = 168.0
# Fewer points than this and the trend is mostly noise.
MIN_POINTS = 10

FEATURE_NAMES = [
    "level",               # latest value (smoothed) / critical limit
    "mean_level",          # average over the window / critical limit
    "warning_ratio",       # warning limit / critical limit (how much room the sensor has between the two)
    "slope_per_hour",      # trend in critical limits per hour (0.1 = climbing 10% of the limit every hour)
    "volatility",          # spread around the trend / critical limit
    "time_above_warning",  # share of the window spent at or above the warning limit
    "trend_strength",      # t-statistic of the slope: is the trend real or just noise? (clipped to +-50)
    "log_hours_to_critical",  # log(1 + hours until the trend line meets the critical limit), capped at a week
]
# Below this t-statistic a slope is treated as noise and not projected.
SIGNIFICANT_TREND = 3.0


@dataclass(frozen=True)
class SensorWindow:
    """Readings of one sensor, oldest first. `hours` are relative times in hours, `values` are in the sensor's unit."""

    hours: np.ndarray
    values: np.ndarray
    warning: float
    critical: float


@dataclass(frozen=True)
class Trend:
    level: float
    slope_per_hour: float
    strength: float
    volatility: float
    hours_to_critical: float | None


def trend(window: SensorWindow) -> Trend:
    """Least-squares line through the window, in critical limits.

    `hours_to_critical` is None unless the sensor is clearly rising: on a short, noisy window a flat sensor
    still has a small random slope, and projecting it would announce failures that are only noise.
    """
    t = window.hours
    y = window.values / window.critical
    n = len(y)
    if t[-1] > t[0] and n > 2:
        slope, intercept = np.polyfit(t, y, 1)
        residuals = y - (slope * t + intercept)
        volatility = float(np.std(residuals))
        spread = float(np.sum((t - t.mean()) ** 2))
        standard_error = np.sqrt(np.sum(residuals ** 2) / (n - 2) / spread) if spread > 0 else 0.0
        strength = float(np.clip(slope / standard_error, -50, 50)) if standard_error > 1e-12 else float(np.sign(slope) * 50)
        slope = float(slope)
    else:
        slope, volatility, strength = 0.0, float(np.std(y)), 0.0
    # The mean of the last tenth of the window: one noisy point must not decide the current level.
    tail = max(3, n // 10)
    level = float(np.mean(y[-tail:]))
    if level >= 1.0:
        hours = 0.0
    elif slope > 0 and strength >= SIGNIFICANT_TREND:
        hours = min((1.0 - level) / slope, MAX_PROJECTION_HOURS)
    else:
        hours = None
    return Trend(level=level, slope_per_hour=slope, strength=strength, volatility=volatility, hours_to_critical=hours)


def sensor_features(window: SensorWindow) -> np.ndarray:
    if len(window.values) < MIN_POINTS:
        raise ValueError(f"need at least {MIN_POINTS} readings, got {len(window.values)}")
    if window.critical <= 0:
        raise ValueError("critical limit must be positive")
    tr = trend(window)
    hours = MAX_PROJECTION_HOURS if tr.hours_to_critical is None else tr.hours_to_critical
    return np.array([
        tr.level,
        float(np.mean(window.values / window.critical)),
        window.warning / window.critical,
        tr.slope_per_hour,
        tr.volatility,
        float(np.mean(window.values >= window.warning)),
        tr.strength,
        float(np.log1p(hours)),
    ])
