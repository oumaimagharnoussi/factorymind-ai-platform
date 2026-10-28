# Failure-risk model

Estimates, for each sensor, the probability that it reaches its critical limit within 24 hours, from a window of
recent readings. Used by ai-insights-service through `POST /predict`. Design: [ADR 0005](../../docs/adr/0005-ai-insights-ml.md).

```
factorymind_ml/features.py   window of readings -> 8 features, relative to the sensor's limits (training and serving)
factorymind_ml/simulate.py   synthetic run-to-failure sensor lives and labelled windows
factorymind_ml/train.py      random forest, evaluation on unseen sensor lives, model card
factorymind_ml/api.py        FastAPI: /predict, /model, /health
```

```bash
python -m venv .venv
.venv/bin/pip install -r requirements-dev.txt        # Windows: .venv\Scripts\pip install -r requirements-dev.txt
python -m pytest -q
python -m factorymind_ml.train --out model           # about 10 s, prints the metrics
uvicorn factorymind_ml.api:app --port 8090           # http://localhost:8090/docs
```

Example:

```bash
curl -s localhost:8090/predict -H 'Content-Type: application/json' -d '{
  "assetId": "M-102",
  "sensors": [{"sensorType": "TEMPERATURE", "unit": "°C", "warningThreshold": 80, "criticalThreshold": 95,
               "readings": [{"recordedAt": "2026-10-28T10:00:00Z", "value": 56}, ...]}]}'
```

The answer has the machine's `riskScore` and `riskLevel` (LOW < 0.4 ≤ MEDIUM < 0.7 ≤ HIGH), and for each sensor its
probability, trend per hour, hours until the trend meets the critical limit, and the reasons in plain words.
