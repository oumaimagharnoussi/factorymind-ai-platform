-- ai-insights-service owns this schema. Machines, alerts and work orders are referenced by id only.

-- One row per scoring of a machine by the model: the risk chart, and the last level after a restart.
CREATE TABLE risk_assessments (
    id                 UUID PRIMARY KEY,
    asset_id           UUID          NOT NULL,
    risk_score         NUMERIC(4, 3) NOT NULL CHECK (risk_score BETWEEN 0 AND 1),
    risk_level         VARCHAR(10)   NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    main_sensor_type   VARCHAR(30)   NOT NULL,
    hours_to_critical  DOUBLE PRECISION,
    model_version      VARCHAR(40)   NOT NULL,
    assessed_at        TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_risk_assessments_asset_time ON risk_assessments (asset_id, assessed_at DESC);
CREATE INDEX ix_risk_assessments_time ON risk_assessments (assessed_at);

-- Read model: latest state of each alert (alert-service) and work order (maintenance-service), from Kafka.
CREATE TABLE alert_snapshots (
    alert_id     UUID PRIMARY KEY,
    asset_id     UUID           NOT NULL,
    sensor_type  VARCHAR(30)    NOT NULL,
    severity     VARCHAR(10)    NOT NULL,
    status       VARCHAR(15)    NOT NULL,
    active       BOOLEAN        NOT NULL,
    last_value   NUMERIC(12, 3),
    unit         VARCHAR(20),
    raised_at    TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_alert_snapshots_asset ON alert_snapshots (asset_id, raised_at DESC);
CREATE INDEX ix_alert_snapshots_status ON alert_snapshots (status);

CREATE TABLE work_order_snapshots (
    work_order_id  UUID PRIMARY KEY,
    code           VARCHAR(20)   NOT NULL,
    asset_id       UUID          NOT NULL,
    type           VARCHAR(15)   NOT NULL,
    priority       VARCHAR(10)   NOT NULL,
    status         VARCHAR(15)   NOT NULL,
    title          VARCHAR(160)  NOT NULL,
    assignee       VARCHAR(100),
    updated_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_work_order_snapshots_asset ON work_order_snapshots (asset_id, updated_at DESC);
CREATE INDEX ix_work_order_snapshots_status ON work_order_snapshots (status);
