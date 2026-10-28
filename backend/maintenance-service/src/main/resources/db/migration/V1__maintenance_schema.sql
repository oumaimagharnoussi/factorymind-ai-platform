-- Maintenance service owns this schema. Machines (asset-service), alerts (alert-service) and spare parts
-- (inventory-service) are referenced by id only, never by a foreign key across services.

CREATE SEQUENCE work_order_number_seq;

CREATE TABLE work_orders (
    id            UUID PRIMARY KEY,
    code          VARCHAR(20)   NOT NULL UNIQUE,
    asset_id      UUID          NOT NULL,
    type          VARCHAR(15)   NOT NULL CHECK (type IN ('CORRECTIVE', 'PREVENTIVE', 'INSPECTION')),
    priority      VARCHAR(10)   NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status        VARCHAR(15)   NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    title         VARCHAR(160)  NOT NULL,
    description   VARCHAR(2000),
    assignee      VARCHAR(100),
    due_date      DATE,
    requested_by  VARCHAR(100)  NOT NULL,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    resolution    VARCHAR(2000),
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_work_orders_completed CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND resolution IS NOT NULL))
);
CREATE INDEX ix_work_orders_asset ON work_orders (asset_id);
CREATE INDEX ix_work_orders_status ON work_orders (status);
CREATE INDEX ix_work_orders_created ON work_orders (created_at DESC);

-- Alerts a work order answers. The primary key keeps an alert on one work order only,
-- even if Kafka delivers the same alert twice.
CREATE TABLE work_order_alerts (
    alert_id       UUID PRIMARY KEY,
    work_order_id  UUID NOT NULL REFERENCES work_orders (id)
);
CREATE INDEX ix_work_order_alerts_order ON work_order_alerts (work_order_id);

-- Parts taken out of stock for a work order. The id is the request id of the stock movement in inventory-service.
CREATE TABLE work_order_parts (
    id             UUID PRIMARY KEY,
    work_order_id  UUID           NOT NULL REFERENCES work_orders (id),
    part_id        UUID           NOT NULL,
    part_number    VARCHAR(40)    NOT NULL,
    part_name      VARCHAR(120)   NOT NULL,
    unit           VARCHAR(20)    NOT NULL,
    quantity       INTEGER        NOT NULL CHECK (quantity > 0),
    unit_cost      NUMERIC(12, 2) NOT NULL,
    issued_by      VARCHAR(100)   NOT NULL,
    issued_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_work_order_parts_order ON work_order_parts (work_order_id);

-- Append-only history of each work order.
CREATE TABLE work_order_activities (
    id             UUID PRIMARY KEY,
    work_order_id  UUID          NOT NULL REFERENCES work_orders (id),
    type           VARCHAR(20)   NOT NULL,
    message        VARCHAR(500)  NOT NULL,
    actor          VARCHAR(100)  NOT NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_work_order_activities_order ON work_order_activities (work_order_id, occurred_at);
