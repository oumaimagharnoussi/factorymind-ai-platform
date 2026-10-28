"""Trains the failure-risk model and writes it with its model card.

    python -m factorymind_ml.train --out model

The model card (model-card.json) holds the metrics on machines the model never saw, next to the metrics of
the rule the plant uses today (alarm once a sensor is above its warning limit), so the gain is measurable.
"""

import argparse
import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import average_precision_score, brier_score_loss, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import GroupShuffleSplit

from .features import FEATURE_NAMES, HORIZON_HOURS
from .simulate import make_dataset

MODEL_FILE = "model.joblib"
CARD_FILE = "model-card.json"
# Probability from which a sensor counts as "will fail within the horizon" in the metrics below.
DECISION_THRESHOLD = 0.5


def binary_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict:
    return {
        "precision": round(float(precision_score(y_true, y_pred, zero_division=0)), 3),
        "recall": round(float(recall_score(y_true, y_pred, zero_division=0)), 3),
    }


def train(lives: int, windows_per_life: int, seed: int) -> tuple[RandomForestClassifier, dict]:
    X, y, groups = make_dataset(lives, windows_per_life, seed)
    split = GroupShuffleSplit(n_splits=1, test_size=0.25, random_state=seed)
    train_idx, test_idx = next(split.split(X, y, groups))

    model = RandomForestClassifier(
        n_estimators=150,
        max_depth=12,
        min_samples_leaf=5,
        n_jobs=-1,
        random_state=seed,
    )
    model.fit(X[train_idx], y[train_idx])

    X_test, y_test = X[test_idx], y[test_idx]
    proba = model.predict_proba(X_test)[:, 1]
    predicted = proba >= DECISION_THRESHOLD
    # Today's rule: an alarm once the sensor is at or above its warning limit.
    level, warning_ratio = X_test[:, 0], X_test[:, 2]
    rule = level >= warning_ratio
    # The failures that the rule cannot see yet: the sensor is still below its warning limit.
    early = (y_test == 1) & ~rule

    metrics = {
        "model": {
            **binary_metrics(y_test, predicted),
            "rocAuc": round(float(roc_auc_score(y_test, proba)), 3),
            "averagePrecision": round(float(average_precision_score(y_test, proba)), 3),
            "brierScore": round(float(brier_score_loss(y_test, proba)), 3),
        },
        "warningThresholdRule": binary_metrics(y_test, rule),
        # Share of upcoming failures flagged while every sensor was still below its warning limit.
        "earlyDetectionRate": round(float(predicted[early].mean()), 3) if early.any() else None,
    }

    trained_at = datetime.now(UTC).replace(microsecond=0)
    fingerprint = hashlib.sha256(
        f"{lives}-{windows_per_life}-{seed}-{FEATURE_NAMES}-{sorted(model.get_params().items())}".encode()).hexdigest()[:8]
    card = {
        "modelVersion": f"rf-{int(HORIZON_HOURS)}h-{fingerprint}",
        "algorithm": "RandomForestClassifier",
        "trainedAt": trained_at.isoformat().replace("+00:00", "Z"),
        "horizonHours": HORIZON_HOURS,
        "decisionThreshold": DECISION_THRESHOLD,
        "trainingData": {
            "source": "synthetic run-to-failure histories (factorymind_ml.simulate)",
            "sensorLives": lives,
            "windows": int(len(y)),
            "positiveShare": round(float(y.mean()), 3),
            "testWindows": int(len(test_idx)),
            "seed": seed,
        },
        "features": FEATURE_NAMES,
        "featureImportances": {
            name: round(float(value), 4) for name, value in zip(FEATURE_NAMES, model.feature_importances_)
        },
        "metrics": metrics,
    }
    return model, card


def save(model: RandomForestClassifier, card: dict, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, out / MODEL_FILE, compress=3)
    (out / CARD_FILE).write_text(json.dumps(card, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the FactoryMind failure-risk model")
    parser.add_argument("--out", type=Path, default=Path("model"))
    parser.add_argument("--lives", type=int, default=3000)
    parser.add_argument("--windows-per-life", type=int, default=8)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    model, card = train(args.lives, args.windows_per_life, args.seed)
    save(model, card, args.out)
    print(json.dumps({"modelVersion": card["modelVersion"], "metrics": card["metrics"]}, indent=2))


if __name__ == "__main__":
    main()
