# ADR 0008: Observability with Prometheus, Grafana and Tempo

- Status: accepted
- Date: 2026-11-02

## Context

FactoryMind now runs eight Spring Boot services, a Python model, Kafka, Postgres and Keycloak. When a machine
turns critical, the event crosses telemetry-service, alert-service, maintenance-service and ai-insights-service
through Kafka. Until now the only way to know whether that chain worked was to read the logs of each container.
We need to answer, without opening a terminal:

- Is every service up, how busy is it, how many requests fail, how slow is it (RED: rate, errors, duration)?
- Is the plant flow healthy: readings coming in, alerts raised and acknowledged, work orders opened, risks scored,
  warehouse loaded?
- For one slow or failed request, where did the time go, across services and Kafka?

## Decision

### Metrics: Micrometer + Prometheus (pull)

- Every Spring Boot service adds `micrometer-registry-prometheus` and exposes `/actuator/prometheus`.
  Every metric carries `application=<spring.application.name>`; `http.server.requests` and
  `http.client.requests` publish histogram buckets so Prometheus can compute p95/p99 per endpoint.
- `/actuator/prometheus` is open without a token, like `/actuator/health`. It is only reachable on the Docker
  network: nginx and Caddy forward `/api` and `/auth` only, never `/actuator`.
- **Prometheus pulls** (scrape every 15 s) rather than services pushing over OTLP: a down service shows as
  `up == 0` at once, and Prometheus is the single place that decides what is kept (7 days in the demo).
- **Business metrics**, each documented in its class, so the dashboards speak the plant's language:

  | Service | Metric | What it shows |
  |---|---|---|
  | telemetry | `factorymind_telemetry_readings_total{status}`, `..._status_changes_total{status}`, `..._readings_duplicate_total` | Readings stored by level, sensors changing level, Kafka redeliveries |
  | alert | `factorymind_alerts_events_total{type,severity}`, `factorymind_alerts_unresolved{severity}`, `..._active`, `..._unacknowledged`, `..._time_to_acknowledge_seconds` | Alert board and operator reaction time |
  | maintenance | `factorymind_workorders_created_total{type,priority,origin}`, `..._closed_total{status,type}`, `factorymind_workorders{status}`, `..._overdue` | Work opened by ALERT, AI_PREDICTION or USER; backlog |
  | inventory | `factorymind_inventory_parts_low_stock`, `..._out_of_stock`, `..._stock_value_euros` | Shelves |
  | asset | `factorymind_assets{status}` | Fleet by status |
  | ai-insights | `factorymind_insights_machines{risk_level}`, `..._risk_score_max`, `..._risk_changes_total{risk_level}` | Predicted failures |
  | analytics | `factorymind_analytics_etl_seconds{status}`, `..._etl_last_success_seconds` | Warehouse loads (Power BI freshness) |
  | ml-service (Python) | `factorymind_ml_predictions_total{risk_level}`, `..._rejected_total`, `..._prediction_duration_seconds`, `..._risk_score` | Model calls (`prometheus-client`, `/metrics`) |

- Counters are counted **after commit** (`@TransactionalEventListener` on the events the services already
  publish), like the Kafka messages, so a rolled-back change is never counted. Gauges that need a database query
  re-read it at most every 10-30 s, however often Prometheus scrapes.
- Calls to other services (`maintenance -> inventory`, `ai-insights -> ml-service` and `asset-service`) now use the
  auto-configured `RestClient.Builder`, so they are measured and carry the trace context.

### Traces: OpenTelemetry + Tempo (push)

- `spring-boot-starter-opentelemetry` (Micrometer Tracing with the OpenTelemetry bridge) in every service; spans
  are sent over OTLP/HTTP to **Grafana Tempo** when `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` is set
  (Docker Compose sets it to `http://tempo:4318/v1/traces`). Without it nothing is exported, and the trace and span
  ids still appear in every log line, so logs of one request can be grepped across services.
- **Kafka carries the trace**: `spring.kafka.template.observation-enabled` and `listener.observation-enabled` put the
  W3C `traceparent` in the record headers. A request that publishes an event and the consumer that handles it
  belong to the same trace.
- `/actuator` requests (scrapes, health probes) are neither measured nor traced (an `ObservationPredicate` in each
  service), and Spring Security's per-filter spans are switched off: Tempo shows plant work, not housekeeping.
- Sampling is 100 % in the demo (`TRACING_SAMPLING_PROBABILITY`), to be lowered under real load.
- Tempo's metrics generator writes the **service graph** and span metrics to Prometheus (remote write), so Grafana
  draws who calls whom, with rate, errors and latency, from the traces themselves.
- **Exemplars**: Prometheus stores the trace id attached to latency buckets; a dot on the latency graph opens the
  trace of that exact request.

### Dashboards and alert rules as code

- Grafana is provisioned from `infrastructure/grafana`: data sources (Prometheus, Tempo, linked both ways) and two
  dashboards, read-only in the UI so the files stay the source of truth:
  - **FactoryMind - Plant operations** (home page): fleet, alerts, work orders by origin, failure risk, model calls,
    spare parts, warehouse freshness.
  - **FactoryMind - Services**: availability, RED per service, slowest endpoints, calls between services, language
    model latency and tokens (Spring AI metrics), Kafka throughput and lag, JVM, connection pools.
- `infrastructure/prometheus/alerts.yml` holds 14 rules in three groups (platform, plant, data and AI): service
  down, 5xx rate, slow API, heap, Kafka lag, critical alert not acknowledged for 10 min, machine at high risk,
  overdue work orders, parts out of stock, no readings for 5 min, warehouse stale or failing, model errors.
  They are checked by `promtool` in CI.

## Consequences

- One URL per question: http://localhost:3000 (Grafana) for dashboards and traces, http://localhost:9090/alerts for
  the rules. Anonymous users can view dashboards in dev; `GRAFANA_ADMIN_PASSWORD` protects editing.
- Three more containers (about 300 MB of RAM together) and a few hundred kB per minute of trace data in the demo.
- Alerts are visible in Prometheus and Grafana but not sent anywhere: an Alertmanager (e-mail, Teams) or the
  planned notification-service would route them.
- Logs stay in Docker (`docker compose logs`), correlated by trace id. Central log search (Loki) is left for later.
- The `service` label comes from the scrape target, so a renamed container must also be renamed in
  `prometheus.yml`.
