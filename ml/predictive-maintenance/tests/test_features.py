import numpy as np
import pytest

from factorymind_ml.features import FEATURE_NAMES, MIN_POINTS, SensorWindow, sensor_features, trend


def window(values, hours=None, warning=80.0, critical=100.0):
    values = np.asarray(values, dtype=float)
    hours = np.linspace(0, 1, len(values)) if hours is None else np.asarray(hours, dtype=float)
    return SensorWindow(hours=hours, values=values, warning=warning, critical=critical)


def test_features_are_relative_to_the_critical_limit():
    small = sensor_features(window(np.linspace(50, 60, 30), warning=80, critical=100))
    large = sensor_features(window(np.linspace(500, 600, 30), warning=800, critical=1000))
    assert len(small) == len(FEATURE_NAMES)
    np.testing.assert_allclose(small, large, atol=1e-12)


def test_rising_trend_is_projected_to_the_critical_limit():
    # From 50 to 60 in one hour: 10 per hour, 40 to go after the last tenth of the window.
    tr = trend(window(np.linspace(50, 60, 100)))
    assert tr.slope_per_hour == pytest.approx(0.10, rel=1e-6)
    assert tr.hours_to_critical == pytest.approx((1 - tr.level) / 0.10)


def test_flat_or_falling_sensor_never_reaches_the_limit():
    assert trend(window(np.full(30, 60.0))).hours_to_critical is None
    assert trend(window(np.linspace(70, 60, 30))).hours_to_critical is None


def test_sensor_over_its_limit_has_no_time_left():
    assert trend(window(np.full(30, 101.0))).hours_to_critical == 0.0


def test_time_above_warning_counts_readings_at_the_limit():
    features = sensor_features(window([70.0] * 15 + [80.0] * 15))
    assert features[FEATURE_NAMES.index("time_above_warning")] == pytest.approx(0.5)


def test_too_few_readings_are_rejected():
    with pytest.raises(ValueError):
        sensor_features(window(np.full(MIN_POINTS - 1, 60.0)))
