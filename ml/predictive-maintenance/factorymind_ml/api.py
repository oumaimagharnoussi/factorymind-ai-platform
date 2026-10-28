"""Prediction API used by ai-insights-service. Internal only: it is reached on the Docker network, never
through the API gateway, so it carries no authentication of its own.

    uvicorn factorymind_ml.api:app --port 8090
"""

import json
import os
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Literal

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

from .features import HORIZON_HOURS, MIN_POINTS, SensorWindow, sensor_features, trend
from .train import CARD_FILE, MODEL_FILE

# Machine risk bands, on the probability of reaching a critical limit within the horizon.
MEDIUM_FROM = 0.4
HIGH_FROM = 0.7

RiskLevel = Literal["LOW", "MEDIUM", "HIGH"]


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class Reading(CamelModel):
    recorded_at: datetime
    value: float


class SensorInput(CamelModel):
    sensor_type: str
    unit: str | None = None
    warning_threshold: float = Field(gt=0)
    critical_threshold: float = Field(gt=0)
    readings: list[Reading]

    @model_validator(mode="after")
    def limits_in_order(self):
        if self.warning_threshold >= self.critical_threshold:
            raise ValueError("warningThreshold must be below criticalThreshold")
        return self


class PredictionRequest(CamelModel):
    asset_id: str
    sensors: list[SensorInput] = Field(min_length=1)


class SensorRisk(CamelModel):
    sensor_type: str
    unit: str | None
    current_value: float
    failure_probability: float
    risk_level: RiskLevel
    trend_per_hour: float
    hours_to_critical: float | None
    drivers: list[str]


class PredictionResponse(CamelModel):
    asset_id: str
    risk_score: float
    risk_level: RiskLevel
    horizon_hours: float
    main_sensor_type: str
    sensors: list[SensorRisk]
    model_version: str


def risk_level(probability: float) -> RiskLevel:
    if probability >= HIGH_FROM:
        return "HIGH"
    if probability >= MEDIUM_FROM:
        return "MEDIUM"
    return "LOW"


def drivers(window: SensorWindow, features: np.ndarray, hours_to_critical: float | None, unit: str) -> list[str]:
    """Plain-language reasons, worst first, so an operator sees why a score is high without reading the model."""
    level, _, _, slope, volatility, above_warning, _, _ = features
    reasons = []
    if level >= 1.0:
        reasons.append("already at or above its critical limit")
    elif hours_to_critical is not None and hours_to_critical <= HORIZON_HOURS:
        reasons.append(f"trend reaches the critical limit in about {format_hours(hours_to_critical)}")
    if above_warning > 0:
        reasons.append(f"{above_warning:.0%} of recent readings at or above the warning limit")
    if slope > 0.005 and hours_to_critical is not None:
        reasons.append(f"rising {slope * window.critical:.2f} {unit}/h ({slope:.1%} of the critical limit per hour)")
    if volatility > 0.05:
        reasons.append("unusually unstable readings")
    if not reasons:
        reasons.append(f"stable at {level:.0%} of the critical limit")
    return reasons


def format_hours(hours: float) -> str:
    if hours < 1:
        return f"{max(1, round(hours * 60))} min"
    return f"{hours:.1f} h"


class Predictor:
    def __init__(self, model_dir: Path):
        self.model = joblib.load(model_dir / MODEL_FILE)
        self.card = json.loads((model_dir / CARD_FILE).read_text(encoding="utf-8"))

    def predict(self, request: PredictionRequest) -> PredictionResponse:
        windows, inputs = [], []
        for sensor in request.sensors:
            if len(sensor.readings) < MIN_POINTS:
                continue
            readings = sorted(sensor.readings, key=lambda r: r.recorded_at)
            start = readings[0].recorded_at
            windows.append(SensorWindow(
                hours=np.array([(r.recorded_at - start).total_seconds() / 3600 for r in readings]),
                values=np.array([r.value for r in readings]),
                warning=sensor.warning_threshold,
                critical=sensor.critical_threshold,
            ))
            inputs.append(sensor)
        if not windows:
            raise ValueError(f"every sensor needs at least {MIN_POINTS} readings")

        features = np.array([sensor_features(w) for w in windows])
        probabilities = self.model.predict_proba(features)[:, 1]
        risks = []
        for sensor, window, row, p in zip(inputs, windows, features, probabilities):
            tr = trend(window)
            hours = None if tr.hours_to_critical is None or tr.hours_to_critical >= 168 else round(tr.hours_to_critical, 2)
            unit = sensor.unit or ""
            risks.append(SensorRisk(
                sensor_type=sensor.sensor_type,
                unit=sensor.unit,
                current_value=round(float(window.values[-1]), 2),
                failure_probability=round(float(p), 3),
                risk_level=risk_level(float(p)),
                trend_per_hour=round(tr.slope_per_hour * window.critical, 3),
                hours_to_critical=hours,
                drivers=drivers(window, row, tr.hours_to_critical, unit),
            ))
        risks.sort(key=lambda r: r.failure_probability, reverse=True)
        # A machine is as close to failing as its worst sensor.
        worst = risks[0]
        return PredictionResponse(
            asset_id=request.asset_id,
            risk_score=worst.failure_probability,
            risk_level=worst.risk_level,
            horizon_hours=HORIZON_HOURS,
            main_sensor_type=worst.sensor_type,
            sensors=risks,
            model_version=self.card["modelVersion"],
        )


@lru_cache
def predictor() -> Predictor:
    """Loaded once, on first use; MODEL_DIR is where train.py wrote the model (the Docker image trains it at build)."""
    return Predictor(Path(os.environ.get("MODEL_DIR", "model")))


app = FastAPI(title="FactoryMind predictive maintenance model", version="1.0.0")


@app.get("/health")
def health() -> dict:
    predictor()
    return {"status": "UP"}


@app.get("/model")
def model_card() -> dict:
    return predictor().card


@app.post("/predict", response_model=PredictionResponse, response_model_by_alias=True)
def predict(request: PredictionRequest) -> PredictionResponse:
    try:
        return predictor().predict(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
