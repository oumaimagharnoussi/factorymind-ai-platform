-- analytics-service owns this database (analytics_db). It never writes to a service database: it reads them
-- (read-only connections), copies what the reports need into "staging", and builds the star schema in "dw".
-- Power BI reads the views of "bi" only, so the tables underneath can change without breaking the reports.

CREATE SCHEMA staging;
CREATE SCHEMA dw;
CREATE SCHEMA bi;

-- Date keys are UTC calendar days, the same for every plant and every fact table.
CREATE FUNCTION dw.date_key(ts TIMESTAMPTZ) RETURNS INTEGER
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN to_char(ts AT TIME ZONE 'UTC', 'YYYYMMDD')::int;

CREATE FUNCTION dw.hours(duration INTERVAL) RETURNS NUMERIC
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    RETURN round(extract(epoch FROM duration) / 3600, 2);

-- ---------------------------------------------------------------------------------------------------------------
-- Staging: a copy of the source rows, replaced on every run (small tables) or appended (risk episodes).
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE staging.assets (
    asset_id          UUID PRIMARY KEY,
    asset_code        VARCHAR(40)  NOT NULL,
    name              VARCHAR(120) NOT NULL,
    manufacturer      VARCHAR(120),
    model             VARCHAR(120),
    criticality       VARCHAR(20)  NOT NULL,
    installed_at      DATE,
    status            VARCHAR(20)  NOT NULL,
    line_name         VARCHAR(120) NOT NULL,
    factory_name      VARCHAR(120) NOT NULL,
    factory_location  VARCHAR(200) NOT NULL
);

-- Sensor alarm limits: the demo history draws readings around them.
CREATE TABLE staging.asset_sensors (
    asset_id            UUID           NOT NULL,
    sensor_type         VARCHAR(30)    NOT NULL,
    unit                VARCHAR(20)    NOT NULL,
    warning_threshold   NUMERIC(12, 3) NOT NULL,
    critical_threshold  NUMERIC(12, 3) NOT NULL,
    PRIMARY KEY (asset_id, sensor_type)
);

CREATE TABLE staging.work_orders (
    work_order_id  UUID PRIMARY KEY,
    code           VARCHAR(20)    NOT NULL,
    asset_id       UUID           NOT NULL,
    type           VARCHAR(15)    NOT NULL,
    priority       VARCHAR(10)    NOT NULL,
    status         VARCHAR(15)    NOT NULL,
    assignee       VARCHAR(100),
    requested_by   VARCHAR(100)   NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    parts_cost     NUMERIC(12, 2) NOT NULL
);

CREATE TABLE staging.work_order_alerts (
    alert_id       UUID PRIMARY KEY,
    work_order_id  UUID NOT NULL
);

-- Alerts that reached CRITICAL: what the reports call a failure.
CREATE TABLE staging.critical_alerts (
    alert_id     UUID PRIMARY KEY,
    asset_id     UUID        NOT NULL,
    sensor_type  VARCHAR(30) NOT NULL,
    failed_at    TIMESTAMPTZ NOT NULL
);

-- A period during which the model rated a machine HIGH risk, built by RiskEpisodes from the risk assessments.
CREATE TABLE staging.risk_episodes (
    asset_id           UUID          NOT NULL,
    started_at         TIMESTAMPTZ   NOT NULL,
    ended_at           TIMESTAMPTZ,
    risk_score         NUMERIC(4, 3) NOT NULL,
    peak_score         NUMERIC(4, 3) NOT NULL,
    main_sensor_type   VARCHAR(30)   NOT NULL,
    hours_to_critical  DOUBLE PRECISION,
    model_version      VARCHAR(40)   NOT NULL,
    PRIMARY KEY (asset_id, started_at)
);

-- The last risk level seen per machine, so an episode that spans two runs is counted once.
CREATE TABLE staging.risk_levels (
    asset_id       UUID PRIMARY KEY,
    risk_level     VARCHAR(10) NOT NULL,
    model_version  VARCHAR(40) NOT NULL,
    assessed_at    TIMESTAMPTZ NOT NULL
);

-- Readings per machine, sensor and hour since the last run, before they are matched to dim_asset.
CREATE TABLE staging.telemetry_hourly (
    asset_id           UUID           NOT NULL,
    sensor_type        VARCHAR(30)    NOT NULL,
    hour_start         TIMESTAMPTZ    NOT NULL,
    unit               VARCHAR(20)    NOT NULL,
    readings           INTEGER        NOT NULL,
    avg_value          NUMERIC(12, 3) NOT NULL,
    min_value          NUMERIC(12, 3) NOT NULL,
    max_value          NUMERIC(12, 3) NOT NULL,
    warning_readings   INTEGER        NOT NULL,
    critical_readings  INTEGER        NOT NULL,
    PRIMARY KEY (asset_id, sensor_type, hour_start)
);

-- ---------------------------------------------------------------------------------------------------------------
-- Star schema
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE dw.dim_date (
    date_key      INTEGER PRIMARY KEY,          -- 20261030
    date          DATE        NOT NULL UNIQUE,
    year          SMALLINT    NOT NULL,
    quarter       SMALLINT    NOT NULL,
    quarter_name  VARCHAR(7)  NOT NULL,         -- 2026 Q4
    month         SMALLINT    NOT NULL,
    month_name    VARCHAR(10) NOT NULL,
    year_month    VARCHAR(7)  NOT NULL,         -- 2026-10, sorts as text
    month_start   DATE        NOT NULL,
    iso_week      SMALLINT    NOT NULL,
    day_of_month  SMALLINT    NOT NULL,
    day_of_week   SMALLINT    NOT NULL,         -- 1 = Monday
    day_name      VARCHAR(10) NOT NULL,
    is_weekend    BOOLEAN     NOT NULL
);

INSERT INTO dw.dim_date
SELECT to_char(d, 'YYYYMMDD')::int, d, extract(year FROM d), extract(quarter FROM d),
       extract(year FROM d) || ' Q' || extract(quarter FROM d),
       extract(month FROM d), trim(to_char(d, 'Month')), to_char(d, 'YYYY-MM'), date_trunc('month', d)::date,
       extract(week FROM d), extract(day FROM d), extract(isodow FROM d), trim(to_char(d, 'Day')),
       extract(isodow FROM d) >= 6
FROM generate_series(DATE '2020-01-01', DATE '2035-12-31', INTERVAL '1 day') AS g (d);

-- Type 1 dimension: a machine keeps one row, updated in place; a machine deleted in asset-service stays
-- (its history still points at it) and is marked removed.
CREATE TABLE dw.dim_asset (
    asset_key         SERIAL PRIMARY KEY,
    asset_id          UUID         NOT NULL UNIQUE,
    asset_code        VARCHAR(40)  NOT NULL,
    asset_name        VARCHAR(120) NOT NULL,
    manufacturer      VARCHAR(120),
    model             VARCHAR(120),
    criticality       VARCHAR(20)  NOT NULL,
    installed_at      DATE,
    current_status    VARCHAR(20)  NOT NULL,
    line_name         VARCHAR(120) NOT NULL,
    factory_name      VARCHAR(120) NOT NULL,
    factory_location  VARCHAR(200) NOT NULL,
    removed           BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE dw.dim_failure_type (
    failure_type_key  SMALLINT PRIMARY KEY,
    code              VARCHAR(20) NOT NULL UNIQUE,
    name              VARCHAR(60) NOT NULL,
    category          VARCHAR(30) NOT NULL,
    sensor_type       VARCHAR(30)
);

INSERT INTO dw.dim_failure_type VALUES
    (0, 'REPORTED',    'Reported by staff',   'Unplanned', NULL),
    (1, 'OVERHEATING', 'Overheating',         'Unplanned', 'TEMPERATURE'),
    (2, 'VIBRATION',   'Excessive vibration', 'Unplanned', 'VIBRATION'),
    (3, 'PRESSURE',    'Pressure anomaly',    'Unplanned', 'PRESSURE'),
    (4, 'SPEED',       'Speed deviation',     'Unplanned', 'ROTATION_SPEED'),
    (5, 'PREDICTED',   'Predicted by AI',     'Planned',   NULL),
    (6, 'SCHEDULED',   'Scheduled service',   'Planned',   NULL);

CREATE TABLE dw.dim_technician (
    technician_key  SERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE
);
INSERT INTO dw.dim_technician (technician_key, username) VALUES (0, '(unassigned)');

-- LIVE rows come from the services and are rebuilt by every run; SIMULATED rows are the demo history
-- (DemoHistory) and are never touched by the ETL.
CREATE TABLE dw.fact_maintenance (
    work_order_id       UUID PRIMARY KEY,
    work_order_code     VARCHAR(20)    NOT NULL,
    asset_key           INTEGER        NOT NULL REFERENCES dw.dim_asset,
    technician_key      INTEGER        NOT NULL REFERENCES dw.dim_technician,
    failure_type_key    SMALLINT       NOT NULL REFERENCES dw.dim_failure_type,
    created_date_key    INTEGER        NOT NULL REFERENCES dw.dim_date,
    completed_date_key  INTEGER        REFERENCES dw.dim_date,
    maintenance_type    VARCHAR(15)    NOT NULL,   -- CORRECTIVE, PREVENTIVE, INSPECTION
    category            VARCHAR(10)    NOT NULL,   -- CORRECTIVE or PLANNED
    origin              VARCHAR(10)    NOT NULL,   -- ALERT, AI, MANUAL
    priority            VARCHAR(10)    NOT NULL,
    status              VARCHAR(15)    NOT NULL,
    failed_at           TIMESTAMPTZ,               -- corrective only: first critical alert, or the order itself
    created_at          TIMESTAMPTZ    NOT NULL,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    response_hours      NUMERIC(10, 2),            -- failure (or creation) to work started
    repair_hours        NUMERIC(10, 2),            -- work started to completed
    restore_hours       NUMERIC(10, 2),            -- corrective: failure to completed (MTTR)
    parts_cost          NUMERIC(12, 2) NOT NULL,
    labor_cost          NUMERIC(12, 2) NOT NULL,
    source              VARCHAR(10)    NOT NULL CHECK (source IN ('LIVE', 'SIMULATED'))
);
CREATE INDEX ix_fact_maintenance_created ON dw.fact_maintenance (created_date_key);
CREATE INDEX ix_fact_maintenance_completed ON dw.fact_maintenance (completed_date_key);

-- One row per downtime event and calendar day (UTC), so a stop over midnight counts in both days
-- and availability per day or month adds up exactly.
CREATE TABLE dw.fact_downtime (
    downtime_id       UUID           NOT NULL,
    date_key          INTEGER        NOT NULL REFERENCES dw.dim_date,
    asset_key         INTEGER        NOT NULL REFERENCES dw.dim_asset,
    failure_type_key  SMALLINT       NOT NULL REFERENCES dw.dim_failure_type,
    downtime_type     VARCHAR(10)    NOT NULL CHECK (downtime_type IN ('UNPLANNED', 'PLANNED')),
    event_start       TIMESTAMPTZ    NOT NULL,
    event_end         TIMESTAMPTZ,                -- NULL while the machine is still down
    is_event_start    BOOLEAN        NOT NULL,    -- true on the first day: count events with it
    hours             NUMERIC(10, 3) NOT NULL,
    source            VARCHAR(10)    NOT NULL CHECK (source IN ('LIVE', 'SIMULATED')),
    PRIMARY KEY (downtime_id, date_key)
);
CREATE INDEX ix_fact_downtime_date ON dw.fact_downtime (date_key);

-- Each HIGH-risk prediction and its outcome, plus one MISSED row per failure the model did not see coming.
CREATE TABLE dw.fact_prediction (
    prediction_id      UUID PRIMARY KEY,
    date_key           INTEGER       NOT NULL REFERENCES dw.dim_date,
    asset_key          INTEGER       NOT NULL REFERENCES dw.dim_asset,
    model_version      VARCHAR(40)   NOT NULL,
    outcome            VARCHAR(15)   NOT NULL
        CHECK (outcome IN ('TRUE_POSITIVE', 'FALSE_POSITIVE', 'PENDING', 'MISSED')),
    predicted_at       TIMESTAMPTZ,
    risk_score         NUMERIC(4, 3),
    main_sensor_type   VARCHAR(30),
    failure_at         TIMESTAMPTZ,
    lead_time_hours    NUMERIC(10, 2),
    source             VARCHAR(10)   NOT NULL CHECK (source IN ('LIVE', 'SIMULATED'))
);
CREATE INDEX ix_fact_prediction_date ON dw.fact_prediction (date_key);

-- Readings summarised per machine, sensor and hour. Kept here long after telemetry-service purges them.
CREATE TABLE dw.fact_telemetry_hourly (
    asset_key          INTEGER        NOT NULL REFERENCES dw.dim_asset,
    sensor_type        VARCHAR(30)    NOT NULL,
    hour_start         TIMESTAMPTZ    NOT NULL,
    date_key           INTEGER        NOT NULL REFERENCES dw.dim_date,
    hour_of_day        SMALLINT       NOT NULL,
    unit               VARCHAR(20)    NOT NULL,
    readings           INTEGER        NOT NULL,
    avg_value          NUMERIC(12, 3) NOT NULL,
    min_value          NUMERIC(12, 3) NOT NULL,
    max_value          NUMERIC(12, 3) NOT NULL,
    warning_readings   INTEGER        NOT NULL,
    critical_readings  INTEGER        NOT NULL,
    source             VARCHAR(10)    NOT NULL CHECK (source IN ('LIVE', 'SIMULATED')),
    PRIMARY KEY (asset_key, sensor_type, hour_start)
);
CREATE INDEX ix_fact_telemetry_date ON dw.fact_telemetry_hourly (date_key);

-- ---------------------------------------------------------------------------------------------------------------
-- Pipeline bookkeeping
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE dw.etl_runs (
    id           BIGSERIAL PRIMARY KEY,
    trigger      VARCHAR(10)  NOT NULL,   -- SCHEDULE or MANUAL
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    row_counts   VARCHAR(500),
    error        VARCHAR(1000)
);

-- High-water marks of the incremental extracts (telemetry hours, risk assessments), and when the model
-- started scoring (failures before that are not counted as missed by the model).
CREATE TABLE dw.etl_watermarks (
    name   VARCHAR(40) PRIMARY KEY,
    value  TIMESTAMPTZ NOT NULL
);
