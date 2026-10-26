-- Asset service owns this schema. Other services refer to machines by asset_code / id only.

CREATE TABLE factories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    location    VARCHAR(200) NOT NULL,
    timezone    VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_factories_name ON factories (LOWER(name));

CREATE TABLE production_lines (
    id          UUID PRIMARY KEY,
    factory_id  UUID         NOT NULL REFERENCES factories (id),
    name        VARCHAR(120) NOT NULL,
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING', 'IDLE', 'STOPPED')),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_lines_factory_name ON production_lines (factory_id, LOWER(name));

CREATE TABLE assets (
    id            UUID PRIMARY KEY,
    asset_code    VARCHAR(40)  NOT NULL,
    name          VARCHAR(120) NOT NULL,
    line_id       UUID         NOT NULL REFERENCES production_lines (id),
    manufacturer  VARCHAR(120),
    model         VARCHAR(120),
    criticality   VARCHAR(20)  NOT NULL CHECK (criticality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    installed_at  DATE,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('OPERATIONAL', 'WARNING', 'STOPPED', 'MAINTENANCE')),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_assets_code ON assets (UPPER(asset_code));
CREATE INDEX ix_assets_line ON assets (line_id);
CREATE INDEX ix_assets_status ON assets (status);

CREATE TABLE sensor_definitions (
    id                  UUID PRIMARY KEY,
    asset_id            UUID           NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    sensor_type         VARCHAR(30)    NOT NULL
        CHECK (sensor_type IN ('TEMPERATURE', 'VIBRATION', 'PRESSURE', 'ROTATION_SPEED')),
    unit                VARCHAR(20)    NOT NULL,
    warning_threshold   NUMERIC(12, 3) NOT NULL,
    critical_threshold  NUMERIC(12, 3) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_sensor_thresholds CHECK (warning_threshold < critical_threshold),
    CONSTRAINT ux_sensor_asset_type UNIQUE (asset_id, sensor_type)
);
