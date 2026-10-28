-- Alert service owns this schema. Machines are referenced by the asset id from asset-service, never by a foreign key.

-- Debounce state per sensor, moved by factorymind.telemetry.sensor-status (see LevelTimer).
CREATE TABLE sensor_watches (
    asset_id               UUID           NOT NULL,
    sensor_type            VARCHAR(30)    NOT NULL,
    status                 VARCHAR(10)    NOT NULL CHECK (status IN ('NORMAL', 'WARNING', 'CRITICAL', 'UNKNOWN')),
    value                  NUMERIC(12, 3),
    unit                   VARCHAR(20),
    warning_threshold      NUMERIC(12, 3),
    critical_threshold     NUMERIC(12, 3),
    changed_at             TIMESTAMPTZ    NOT NULL,
    warning_above_since    TIMESTAMPTZ,
    warning_below_since    TIMESTAMPTZ,
    warning_above_ms       BIGINT         NOT NULL DEFAULT 0,
    warning_latched        BOOLEAN        NOT NULL DEFAULT FALSE,
    critical_above_since   TIMESTAMPTZ,
    critical_below_since   TIMESTAMPTZ,
    critical_above_ms      BIGINT         NOT NULL DEFAULT 0,
    critical_latched       BOOLEAN        NOT NULL DEFAULT FALSE,
    version                BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (asset_id, sensor_type)
);

CREATE TABLE alerts (
    id                  UUID PRIMARY KEY,
    asset_id            UUID           NOT NULL,
    sensor_type         VARCHAR(30)    NOT NULL,
    severity            VARCHAR(10)    NOT NULL CHECK (severity IN ('WARNING', 'CRITICAL')),
    status              VARCHAR(15)    NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    active              BOOLEAN        NOT NULL,
    current_status      VARCHAR(10)    NOT NULL,
    last_value          NUMERIC(12, 3),
    unit                VARCHAR(20),
    warning_threshold   NUMERIC(12, 3),
    critical_threshold  NUMERIC(12, 3),
    occurrences         INTEGER        NOT NULL,
    raised_at           TIMESTAMPTZ    NOT NULL,
    last_raised_at      TIMESTAMPTZ    NOT NULL,
    escalated_at        TIMESTAMPTZ,
    cleared_at          TIMESTAMPTZ,
    acknowledged_at     TIMESTAMPTZ,
    acknowledged_by     VARCHAR(100),
    resolved_at         TIMESTAMPTZ,
    resolved_by         VARCHAR(100),
    resolution_note     VARCHAR(500),
    work_order_id       UUID,
    work_order_code     VARCHAR(20),
    work_order_status   VARCHAR(15),
    version             BIGINT         NOT NULL DEFAULT 0,
    -- An alert cannot be closed while the sensor is still outside its limits.
    CONSTRAINT ck_alerts_resolved_inactive CHECK (status <> 'RESOLVED' OR NOT active)
);
-- At most one unresolved alert per sensor: a sensor that comes back reuses it.
CREATE UNIQUE INDEX ux_alerts_unresolved ON alerts (asset_id, sensor_type) WHERE status <> 'RESOLVED';
CREATE INDEX ix_alerts_last_raised ON alerts (last_raised_at DESC);
CREATE INDEX ix_alerts_asset ON alerts (asset_id);
