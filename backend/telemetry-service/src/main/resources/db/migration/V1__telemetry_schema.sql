-- Telemetry service owns this schema. Machines are referenced by the asset id from asset-service, never by a foreign key.

-- Local copy of the sensor alarm limits, kept up to date from the factorymind.asset.sensor-definitions topic.
CREATE TABLE sensor_thresholds (
    sensor_id           UUID PRIMARY KEY,
    asset_id            UUID           NOT NULL,
    sensor_type         VARCHAR(30)    NOT NULL,
    unit                VARCHAR(20)    NOT NULL,
    warning_threshold   NUMERIC(12, 3) NOT NULL,
    critical_threshold  NUMERIC(12, 3) NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ux_thresholds_asset_type UNIQUE (asset_id, sensor_type)
);

-- Every reading, kept for a few days (see telemetry.retention). reading_id comes from the producer,
-- so a message that Kafka delivers twice is stored once.
CREATE TABLE sensor_readings (
    reading_id   UUID PRIMARY KEY,
    asset_id     UUID           NOT NULL,
    sensor_type  VARCHAR(30)    NOT NULL,
    value        NUMERIC(12, 3) NOT NULL,
    status       VARCHAR(10)    NOT NULL CHECK (status IN ('NORMAL', 'WARNING', 'CRITICAL', 'UNKNOWN')),
    recorded_at  TIMESTAMPTZ    NOT NULL,
    received_at  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_readings_asset_time ON sensor_readings (asset_id, recorded_at);
CREATE INDEX ix_readings_time ON sensor_readings (recorded_at);

-- The latest reading of each sensor: what the live dashboard shows before the stream sends anything.
CREATE TABLE sensor_states (
    asset_id      UUID           NOT NULL,
    sensor_type   VARCHAR(30)    NOT NULL,
    value         NUMERIC(12, 3) NOT NULL,
    status        VARCHAR(10)    NOT NULL CHECK (status IN ('NORMAL', 'WARNING', 'CRITICAL', 'UNKNOWN')),
    recorded_at   TIMESTAMPTZ    NOT NULL,
    status_since  TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (asset_id, sensor_type)
);
