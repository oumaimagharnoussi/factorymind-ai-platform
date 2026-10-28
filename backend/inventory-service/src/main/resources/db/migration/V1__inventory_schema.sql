-- Inventory service owns this schema. Work orders (maintenance-service) are referenced by id only.

CREATE TABLE spare_parts (
    id                UUID PRIMARY KEY,
    part_number       VARCHAR(40)    NOT NULL UNIQUE,
    name              VARCHAR(120)   NOT NULL,
    description       VARCHAR(500),
    category          VARCHAR(15)    NOT NULL CHECK (category IN ('MECHANICAL', 'ELECTRICAL', 'HYDRAULIC', 'PNEUMATIC',
                                                                   'LUBRICANT', 'FILTER', 'SENSOR', 'CONSUMABLE')),
    unit              VARCHAR(20)    NOT NULL,
    -- Stock can never go negative, whatever the application does.
    quantity_on_hand  INTEGER        NOT NULL CHECK (quantity_on_hand >= 0),
    minimum_stock     INTEGER        NOT NULL CHECK (minimum_stock >= 0),
    unit_cost         NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    location          VARCHAR(40),
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);

-- The stock ledger: quantity_on_hand of a part is the sum of its movements.
CREATE TABLE stock_movements (
    id               UUID PRIMARY KEY,
    part_id          UUID           NOT NULL REFERENCES spare_parts (id),
    type             VARCHAR(15)    NOT NULL CHECK (type IN ('RECEIPT', 'ISSUE', 'RETURN', 'ADJUSTMENT')),
    quantity_change  INTEGER        NOT NULL,
    quantity_after   INTEGER        NOT NULL,
    unit_cost        NUMERIC(12, 2) NOT NULL,
    -- Idempotency key from the caller: the same request never moves stock twice.
    request_id       UUID           UNIQUE,
    work_order_id    UUID,
    work_order_code  VARCHAR(20),
    reference        VARCHAR(60),
    note             VARCHAR(500),
    performed_by     VARCHAR(100)   NOT NULL,
    performed_at     TIMESTAMPTZ    NOT NULL
);
CREATE INDEX ix_movements_part_time ON stock_movements (part_id, performed_at DESC);
CREATE INDEX ix_movements_work_order ON stock_movements (work_order_id);
