import json

import numpy as np

from factorymind_ml.features import SensorWindow, sensor_features
from factorymind_ml.simulate import random_life, sample_window
from factorymind_ml.train import CARD_FILE


def probability(model_dir, values):
    import joblib

    model = joblib.load(model_dir / "model.joblib")
    w = SensorWindow(hours=np.linspace(0, 1, len(values)), values=np.asarray(values, float), warning=80, critical=100)
    return model.predict_proba([sensor_features(w)])[0, 1]


def test_a_steep_climb_scores_higher_than_a_flat_healthy_sensor(model_dir):
    healthy = probability(model_dir, 55 + np.random.default_rng(1).normal(0, 1, 60))
    climbing = probability(model_dir, np.linspace(55, 78, 60))
    assert climbing > 0.7
    assert healthy < 0.3


def test_model_card_compares_the_model_with_the_warning_rule(model_dir):
    card = json.loads((model_dir / CARD_FILE).read_text())
    assert card["metrics"]["model"]["rocAuc"] > 0.8
    assert set(card["metrics"]["warningThresholdRule"]) == {"precision", "recall"}
    assert abs(sum(card["featureImportances"].values()) - 1) < 0.01


def test_simulated_windows_stay_finite():
    rng = np.random.default_rng(3)
    for _ in range(300):
        life = random_life(rng)
        assert np.isfinite(sensor_features(sample_window(life, rng.uniform(0, 1000), rng))).all()
