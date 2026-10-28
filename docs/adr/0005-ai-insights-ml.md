# ADR 0005: Failure risk from a Python model, explained by Spring AI

- Status: accepted
- Date: 2026-10-28

## Context

Alerts (ADR 0004) fire once a sensor is already past a limit. The point of predictive maintenance is to see the
failure coming while the machine still runs normally, and to tell a technician why. FactoryMind has no years of
real breakdowns to learn from, and the team writes Java; the data science ecosystem is Python.

## Decision

### The model (ml/predictive-maintenance, Python, port 8090)

- **Question asked:** will this sensor reach its critical limit within the next 24 hours? A binary classifier
  per sensor; a machine is as close to failing as its worst sensor.
- **Features** are computed from the window of recent readings and divided by the sensor's critical limit, so
  one model serves temperature, vibration and pressure alike: level now, mean level, warning/critical ratio,
  slope per hour, spread around the trend, share of time above warning, trend strength (t-statistic of the
  slope) and log of the hours until the trend line meets the limit. A slope is only projected when it is
  significant (t ≥ 3): on a short noisy window a flat sensor always has some slope.
  The same `features.py` is used for training and serving, so there is no training/serving skew.
- **Training data** is simulated: 3,000 sensor lives (healthy, wearing linearly or with acceleration from minutes
  to weeks, drifting to a plateau below the limit, with noise and spikes), 24,000 labelled windows. Real labelled
  history (work orders closed as failures) can replace the generator without touching the rest.
- **Random forest** (scikit-learn): non-linear, robust to unscaled features, gives feature importances to show,
  and trains in seconds. The split is by sensor life, so test windows come from machines the model never saw.
- **Model card** (`GET /model`) stores metrics next to the rule the plant uses today ("alarm above the warning
  limit"): precision 0.98 vs 0.85, recall 0.77 vs 0.74, ROC AUC 0.90, and 22 % of upcoming failures flagged while
  every sensor was still below its warning limit, which the rule cannot see at all.
- **Risk bands:** LOW below 0.4, MEDIUM up to 0.7, HIGH above. 0.4 keeps healthy demo machines (scores around
  0.2, 99th percentile 0.34) out of MEDIUM.
- The model is trained while the Docker image is built (fixed seed), so the image always carries the model that
  matches its code; the model file is never committed.
- FastAPI, internal only: no route in the gateway and no authentication; only ai-insights-service calls it.

### ai-insights-service (Java, port 8087, `insights_db`)

- Reads `asset.sensor-definitions` (limits, rewound to the beginning on start) and `telemetry.readings` into an
  **in-memory window per sensor** (`INSIGHTS_WINDOW`, 2 min in the demo because the simulator squeezes wear
  into 6 minutes; hours on a real plant). On start it seeks the readings topic to *now − window*, so the windows
  are full again without a table of their own.
- Every `INSIGHTS_SCORING_INTERVAL` (10 s) it posts each machine's windows to `/predict`, keeps the answer in
  memory for the screens, stores one row per scoring in `risk_assessments` (history chart, last level after a
  restart; kept 3 days) and publishes **`factorymind.ai.risk-levels` only when the level changes**.
- If the model is down, the round is skipped and logged; nothing else depends on it.
- It keeps a **read model** of alerts and work orders from their topics, so reports and answers can describe a
  machine's history without calling two services per question.

### maintenance-service

- A machine that becomes HIGH gets an **INSPECTION work order, priority HIGH**, requested by `ai-insights`, unless
  it already has an open work order of any kind. That check also makes a redelivered event harmless.

### Spring AI (reports and assistant)

- `spring.ai.model.chat` picks the chat model: `anthropic` (Claude, needs `ANTHROPIC_API_KEY`), `ollama` (local,
  free) or `none`. Both starters are on the classpath; only the chosen one is configured.
- **Report** (`POST /assets/{id}/report`): the facts (risk per sensor with reasons, peak of the last hour, alerts,
  work orders) are written as text by `MachineFacts`, and the model is told to use only those facts. Without a
  language model, or if it fails, the same facts give a rule-based report; the answer says which wrote it.
- **Assistant** (`POST /ask`): Spring AI **tool calling**. Four read-only tools (`machineRisks`,
  `machineDetails`, `unresolvedAlerts`, `openWorkOrders`) give the model live data; it decides which to call.
  The tool loop runs in the client (`ToolCallingAdvisor`), so it behaves the same with Claude and Ollama, and
  it is tested with a scripted model.
- Reports and the assistant are for ENGINEER and ADMIN (they may cost a paid API call); everyone reads risks.

## Consequences

- The model is only as good as the simulation it learned from; the model card says so and the screen shows the
  reasons behind every score, so nobody has to trust a bare number.
- Risk in a 2-minute window only fits the demo's compressed time. A real plant needs longer windows and real
  failure labels; both are configuration and data, not code changes.
- The in-memory windows and latest risks are per instance. With several instances, each scores the machines of
  its own partitions; the screens would then need a shared store (Redis is planned).
- An inspection opened by the model and a corrective order opened by a later critical alert on the same machine
  are two work orders; linking them is a possible follow-up.
