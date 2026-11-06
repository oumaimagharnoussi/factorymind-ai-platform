# ADR 0010: Centralized logs with Loki and Alloy

- Status: accepted
- Date: 2026-11-06

## Context

Phase 8 gave every request a trace (Tempo) and every service metrics (Prometheus), and printed the trace id in
each log line. Reading the logs themselves was still `docker compose logs <service> | findstr <traceId>`: one
service at a time, gone when a container is recreated, no search across services, no way to alert on what the
logs say.

Two concrete gaps showed up while building this phase:

- a Kafka message that a consumer could not process after its retries was copied to `<topic>-dlt` **without a
  single log line**: Spring Kafka's `DeadLetterPublishingRecoverer` succeeds silently, so a poison message was
  invisible unless someone opened the DLT topic in Kafka UI;
- the logs were text. Finding "all errors of maintenance-service in the trace of this reading" meant a regex on
  a format that differs between Spring Boot, Kafka, Keycloak and the Python model.

## Decision

### Loki for storage, Alloy for collection

- **Grafana Loki 3.7** (single process, filesystem storage on the `loki-data` volume, schema v13 / TSDB, 7 days
  retention by the compactor). It indexes a few labels only and stores lines compressed, which fits a
  laptop running twenty containers better than Elasticsearch (several GB of heap for the same job).
- **Grafana Alloy 1.21** reads the stdout of every container of the Compose project `factorymind` from the
  Docker socket (`discovery.docker` + `loki.source.docker`), so **no service changes how it writes logs** and
  third-party containers (Kafka, Keycloak, Postgres, Grafana) are collected too. Alloy is the successor of
  Promtail, which is deprecated. Read positions are kept on the `alloy-data` volume: a restart sends no line twice.

### What each line carries

| Kind | Name | Why |
|---|---|---|
| Label (indexed) | `service` (Compose service name), `container`, `level` | Few values; the same `service` name as in Prometheus and Tempo |
| Structured metadata | `trace_id`, `span_id`, `logger` | One value per request: indexing them as labels would create a stream per request |
| Line | the original line | `| json` extracts any field at query time |

`level` is normalized by Alloy (`WARNING` → `WARN`, `SEVERE` → `FATAL`, no level word → `INFO`), so
`{level="ERROR"}` finds the errors of every service whatever its logging library.

### JSON logs, switched on by Docker Compose only

Spring Boot's built-in structured logging writes one JSON object per line (logstash format: `@timestamp`, `level`,
`logger_name`, `message`, `stack_trace`, and the MDC, i.e. `traceId` and `spanId`). Compose sets
`LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` in the shared `x-observability` block; a service started from the IDE
keeps the readable text format. A stack trace stays inside one JSON line, so it is not split into thirty "lines"
with no level.

The Python model gets the same keys from a small `logging.Formatter` (`factorymind_ml/logs.py`), loaded by
`uvicorn --log-config` in its image, which also drops the access lines of `/health` and `/metrics` (a probe every
ten seconds is not information).

### Logs, traces and metrics link to each other in Grafana

- A Loki line with a `trace_id` shows a **View trace** button (derived field) that opens the trace in Tempo.
- A Tempo span has **Logs for this span** (`tracesToLogsV2`): the lines of that service with that trace id.
- Dashboard **FactoryMind - Logs**: volume per service, errors and warnings, top error sources, a searchable
  log panel (service, level, free text), and "errors of a request (with trace)".
- Anonymous visitors are Grafana Viewers and cannot use Explore; sign in (admin) to query freely.

### Alerts on logs go through the same Alertmanager

Loki's ruler evaluates LogQL rules (`infrastructure/loki/rules/fake/factorymind.yml`; `fake` is the name of the
single tenant) and sends them to the Alertmanager of ADR 0009, so they reach notification-service, its routing
rules (platform events → IT mailbox) and the delivery log like any Prometheus alert:

- `ErrorLogBurst`: more than 20 ERROR lines in 5 minutes from one service, for 2 minutes;
- `KafkaMessagesDeadLettered`: any record sent to a dead-letter topic in the last 10 minutes.

For the second rule every consuming service's `KafkaConfig` now wraps the dead-letter recoverer: it logs
`Kafka record <topic>-<partition>@<offset> sent to dead-letter topic <topic>-dlt after retries: <cause>` at ERROR
(with the trace id of the failed delivery) and then publishes to the DLT as before.

Loki and Alloy are scraped by Prometheus (`up{service="loki"}`, `up{service="alloy"}`), so `ServiceDown` covers
the log pipeline itself.

## Consequences

- One place to read the logs of the whole platform, kept 7 days, searchable by service, level, text and trace.
- From a red point on a latency graph to the trace (exemplar), from a span to its logs, from a log line to its
  trace: the three signals are connected.
- Poison Kafka messages are now visible and alerted on instead of silently parked.
- `docker compose logs` shows JSON lines; `docker compose logs <service> | findstr ERROR` still works, and Grafana
  is the comfortable way to read them.
- Alloy needs the Docker socket (read-only mount). It can see every container on the machine; the discovery
  filter keeps only project `factorymind`. In production, the socket mount is the usual trade-off of a
  single-host Compose deployment; on Kubernetes the same pipeline would read pod logs from the node instead.
- Not done: log-based metrics recorded into Prometheus, multi-tenant Loki, object storage (S3) for the chunks,
  sampling of noisy INFO logs.
