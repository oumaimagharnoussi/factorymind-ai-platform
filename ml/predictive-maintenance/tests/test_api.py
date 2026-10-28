from datetime import UTC, datetime, timedelta

import numpy as np


def readings(values, step_seconds=3):
    start = datetime(2026, 10, 28, 8, 0, tzinfo=UTC)
    return [{"recordedAt": (start + timedelta(seconds=i * step_seconds)).isoformat(), "value": float(v)}
            for i, v in enumerate(values)]


def sensor(sensor_type, values, warning=80.0, critical=95.0, unit="°C"):
    return {"sensorType": sensor_type, "unit": unit, "warningThreshold": warning,
            "criticalThreshold": critical, "readings": readings(values)}


def test_machine_risk_is_its_worst_sensor(client):
    rng = np.random.default_rng(0)
    body = {"assetId": "a1", "sensors": [
        sensor("VIBRATION", 4 + rng.normal(0, 0.1, 100), warning=7, critical=11, unit="mm/s"),
        # Climbing from 56 to 75 in five minutes: still under the warning limit, but not for long.
        sensor("TEMPERATURE", np.linspace(56, 75, 100)),
    ]}

    response = client.post("/predict", json=body)

    assert response.status_code == 200
    result = response.json()
    assert result["riskLevel"] == "HIGH"
    assert result["mainSensorType"] == "TEMPERATURE"
    assert result["riskScore"] == result["sensors"][0]["failureProbability"]
    temperature = result["sensors"][0]
    assert temperature["hoursToCritical"] < 1
    assert temperature["trendPerHour"] > 200
    assert any("critical limit in about" in d for d in temperature["drivers"])
    assert result["sensors"][1]["riskLevel"] == "LOW"
    assert result["modelVersion"].startswith("rf-24h-")


def test_sensors_with_too_few_readings_are_skipped(client):
    body = {"assetId": "a1", "sensors": [
        sensor("TEMPERATURE", [60.0] * 5),
        sensor("PRESSURE", [5.0] * 30, warning=8, critical=10, unit="bar"),
    ]}
    result = client.post("/predict", json=body).json()
    assert [s["sensorType"] for s in result["sensors"]] == ["PRESSURE"]


def test_no_usable_sensor_is_unprocessable(client):
    body = {"assetId": "a1", "sensors": [sensor("TEMPERATURE", [60.0] * 5)]}
    assert client.post("/predict", json=body).status_code == 422


def test_limits_must_be_in_order(client):
    body = {"assetId": "a1", "sensors": [sensor("TEMPERATURE", [60.0] * 30, warning=95, critical=80)]}
    assert client.post("/predict", json=body).status_code == 422


def test_model_card_and_health(client):
    assert client.get("/health").json() == {"status": "UP"}
    card = client.get("/model").json()
    assert card["horizonHours"] == 24
    assert "log_hours_to_critical" in card["featureImportances"]
