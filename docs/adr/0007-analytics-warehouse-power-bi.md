# ADR 0007: Analytics warehouse and Power BI reports

- Status: accepted
- Date: 2026-10-30

## Context

Plant managers ask questions that no single service can answer: how available were the machines last quarter,
which ones fail most and take longest to fix, what maintenance costs, and does the failure-risk model actually
predict failures. The answers join machines (asset-service), work orders and parts (maintenance-service), alerts
(alert-service), risk scores (ai-insights-service) and readings (telemetry-service), over months. The operational
databases are the wrong place for that: each service owns its schema, telemetry keeps raw readings for three days
only, and heavy report queries must not slow down the plant.

## Decision

### A separate warehouse, loaded by analytics-service

- **analytics_db** in the same Postgres server, owned by a new Spring Boot service, `analytics-service` (8088).
  Three schemas: `staging` (copy of the source rows), `dw` (star schema), `bi` (views for Power BI).
- **Extract by SQL from the service databases**, every 5 minutes. Each source has its own small connection pool,
  and every session starts with `default_transaction_read_only = on`: Postgres refuses any write from the
  pipeline, whatever the user. The source queries read only the columns the warehouse needs.
- **Small tables in full, big ones incrementally.** Machines, work orders and alerts are read in full each run,
  and the LIVE facts are rebuilt from them in one transaction: idempotent, no change tracking, every status change
  picked up. Readings are summarised per machine, sensor and hour *in the source query*, from a high-water mark
  (the current hour is read again next run); risk assessments are read after a high-water mark and turned into
  HIGH-risk episodes in Java (`RiskEpisodes`), carrying each machine's last level across runs.
- **One transaction per load,** guarded by a Postgres advisory lock: a report never sees half a load, and two
  instances never load at once. A failed run changes nothing; the next one retries. Runs are logged in
  `dw.etl_runs`.

Rejected: reading Kafka topics into the warehouse (the topics carry events, not the current state of a work
order, and replaying them to rebuild facts is harder to explain than SQL); Debezium CDC (right for large volumes,
heavy for 7 machines; the pipeline can move to it without changing the star schema); writing to the warehouse
from each service (couples every service to reporting).

### Star schema

| Table | Grain |
|---|---|
| `dim_date` | One UTC day, 2020–2035 |
| `dim_asset` | One machine, with its line and factory (type 1: updated in place; deleted machines are kept and flagged) |
| `dim_failure_type` | Overheating, vibration, pressure, speed (from the alert's sensor), reported by staff, predicted by AI, scheduled service |
| `dim_technician` | One assignee |
| `fact_maintenance` | One work order: failure time, response, repair and restore hours, parts and labour cost |
| `fact_downtime` | One downtime event **per calendar day** (a stop over midnight has two rows), so availability per day or month adds up exactly in SQL and in DAX |
| `fact_prediction` | One HIGH-risk episode with its outcome, plus one row per missed failure |
| `fact_telemetry_hourly` | One machine, sensor and hour: count, average, min, max, readings in warning and critical. Kept long after telemetry purges the raw readings |

Downtime is derived from work orders (a corrective order means the machine is down from the failure until the
order is completed; a preventive order stops it while in progress), because the services do not record machine
stops directly. Labour cost uses a configured hourly rate, the only cost not recorded by the services. The exact
definitions are in [docs/analytics/kpi-definitions.md](../analytics/kpi-definitions.md).

### Demo history

A fresh install has a few hours of data, which makes trend reports empty. On its first load the service writes
180 days of simulated history (`HistoryGenerator`, fixed seed, `source = 'SIMULATED'`): failures getting rarer,
a second model version that catches more of them, predicted failures repaired faster, sensor values climbing
before each failure. The ETL never touches these rows, every report can filter them out, and
`ANALYTICS_DEMO_HISTORY=false` disables them. They are labelled as simulated everywhere they show.

### Power BI on views, KPIs in the app too

- Power BI imports the `bi` views, which carry report names (`"Asset Code"`, `"Availability"` …). The tables
  underneath can change without breaking the reports. Measures are kept as DAX in `analytics/powerbi/measures.dax`
  and, once the report is saved as a Power BI project (.pbip), as TMDL in Git.
- Three reports, as planned: Executive Overview, Reliability & Maintenance, AI Performance
  ([guide](../analytics/power-bi-guide.md)).
- Power BI Embedded needs a paid capacity, so the Angular `/analytics` page shows the headline KPIs from
  `/api/analytics` (same warehouse, same definitions, checked by an integration test against hand-computed values)
  and links to the published report when `POWERBI_REPORT_URL` is set.

## Consequences

- The reports are at most 5 minutes behind the plant (`ANALYTICS_REFRESH_INTERVAL`); Power BI adds its own
  refresh schedule on top.
- analytics-service depends on the services' table structures. The integration test builds the source databases
  from the services' own Flyway migrations, so a renamed column fails the build instead of the night load.
- Full reloads of work orders and alerts are fine for thousands of rows; past that, switch them to `updated_at`
  high-water marks or CDC.
- Measuring a predictive model on the plant it protects undercounts it: a failure prevented by the inspection
  shows up as a false alarm. The KPI document says so.
