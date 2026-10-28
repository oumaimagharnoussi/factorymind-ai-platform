"""Synthetic run-to-failure histories for training.

FactoryMind has no years of real breakdowns to learn from, so we generate sensor lives with the shapes plants
see: machines that stay healthy, machines that wear out (slowly or fast, linearly or accelerating), machines
that drift up and then settle below their limits, and noise with the odd spike. Each sampled window is labelled
by looking at the noise-free future of its sensor: does it reach the critical limit within HORIZON_HOURS?
Real labelled history (work orders closed as failures) would replace this generator without changing the rest.
"""

from dataclasses import dataclass

import numpy as np

from .features import HORIZON_HOURS, SensorWindow, sensor_features


@dataclass(frozen=True)
class SensorLife:
    critical: float
    warning: float
    baseline: float
    noise: float          # relative standard deviation of the measurement noise
    kind: str             # "healthy", "wear" or "plateau"
    onset: float          # hour at which the drift starts
    duration: float       # hours from onset to 110% of the critical limit (wear) or to the plateau
    shape: float          # 0 = linear, > 0 = accelerating wear
    plateau: float        # final level of a "plateau" life, in the sensor's unit
    spike_rate: float     # spikes per hour

    def true_value(self, t: np.ndarray) -> np.ndarray:
        # Past 1.5 the machine would long be stopped; capping also keeps the exponential finite.
        x = np.clip((t - self.onset) / self.duration, 0.0, 1.5)
        if self.kind == "healthy":
            return np.full_like(t, self.baseline, dtype=float)
        if self.kind == "plateau":
            return self.baseline + (self.plateau - self.baseline) * np.clip(x, 0.0, 1.0)
        top = 1.10 * self.critical
        f = x if self.shape == 0 else np.expm1(self.shape * x) / np.expm1(self.shape)
        return self.baseline + (top - self.baseline) * f

    def failure_hour(self) -> float | None:
        """First hour the noise-free value reaches the critical limit, None if it never does."""
        if self.kind != "wear":
            return None
        target = (self.critical - self.baseline) / (1.10 * self.critical - self.baseline)
        x = target if self.shape == 0 else np.log1p(target * np.expm1(self.shape)) / self.shape
        return self.onset + x * self.duration


def random_life(rng: np.random.Generator) -> SensorLife:
    critical = rng.uniform(20, 250)
    warning = critical * rng.uniform(0.70, 0.92)
    baseline = warning * rng.uniform(0.55, 0.88)
    kind = rng.choice(["healthy", "wear", "plateau"], p=[0.35, 0.50, 0.15])
    return SensorLife(
        critical=critical,
        warning=warning,
        baseline=baseline,
        noise=rng.uniform(0.005, 0.05),
        kind=str(kind),
        onset=rng.uniform(0, 200),
        # From a few minutes (a pump running dry) to weeks (a bearing wearing out).
        duration=float(np.exp(rng.uniform(np.log(0.05), np.log(600)))),
        shape=0.0 if rng.random() < 0.5 else rng.uniform(1.0, 4.0),
        plateau=rng.uniform(baseline, critical * 0.97),
        spike_rate=rng.choice([0.0, 0.0, 0.5, 3.0]),
    )


def sample_window(life: SensorLife, end: float, rng: np.random.Generator) -> SensorWindow:
    length = float(np.exp(rng.uniform(np.log(0.05), np.log(8))))
    n = int(rng.integers(20, 200))
    hours = np.sort(end - rng.uniform(0, length, n))
    hours[-1] = end
    values = life.true_value(hours) * (1 + rng.normal(0, life.noise, n))
    spikes = rng.random(n) < life.spike_rate * length / n
    values[spikes] *= rng.uniform(1.1, 1.5, spikes.sum())
    return SensorWindow(hours=hours - hours[0], values=values, warning=life.warning, critical=life.critical)


def label(life: SensorLife, end: float) -> int:
    failure = life.failure_hour()
    return int(failure is not None and failure <= end + HORIZON_HOURS)


def window_end(life: SensorLife, rng: np.random.Generator) -> float:
    failure = life.failure_hour()
    if failure is None or rng.random() < 0.3:
        return rng.uniform(0, 1000)
    # Most windows end before the failure, from a few minutes to many days ahead, so the model sees the
    # whole approach; a few end after it (the sensor is already over its limit).
    ahead = float(np.exp(rng.uniform(np.log(0.01), np.log(400))))
    return failure - ahead if rng.random() < 0.9 else failure + rng.uniform(0, 2)


def make_dataset(lives: int, windows_per_life: int, seed: int):
    """Returns X (features), y (labels) and groups (life id, so a life never sits in both train and test)."""
    rng = np.random.default_rng(seed)
    rows, labels, groups = [], [], []
    for life_id in range(lives):
        life = random_life(rng)
        for _ in range(windows_per_life):
            end = window_end(life, rng)
            rows.append(sensor_features(sample_window(life, end, rng)))
            labels.append(label(life, end))
            groups.append(life_id)
    return np.array(rows), np.array(labels), np.array(groups)
