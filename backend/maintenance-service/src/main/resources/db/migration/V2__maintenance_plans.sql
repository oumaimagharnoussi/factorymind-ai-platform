-- Preventive maintenance plans (docs/adr/0012): a job to repeat on a machine every N days. The planner turns each
-- due occurrence into a PREVENTIVE work order, a few days ahead (lead_days) so it can be scheduled.

CREATE SEQUENCE maintenance_plan_number_seq;

CREATE TABLE maintenance_plans (
    id                 UUID PRIMARY KEY,
    code               VARCHAR(20)   NOT NULL UNIQUE,
    asset_id           UUID          NOT NULL,
    title              VARCHAR(160)  NOT NULL,
    instructions       VARCHAR(2000),
    -- FIXED: every N days on the calendar, late or not; FLOATING: N days after the last job was closed.
    schedule           VARCHAR(10)   NOT NULL CHECK (schedule IN ('FIXED', 'FLOATING')),
    interval_days      INTEGER       NOT NULL CHECK (interval_days BETWEEN 1 AND 3650),
    lead_days          INTEGER       NOT NULL CHECK (lead_days >= 0),
    priority           VARCHAR(10)   NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    assignee           VARCHAR(100),
    active             BOOLEAN       NOT NULL,
    next_due_date      DATE          NOT NULL,
    last_generated_at  TIMESTAMPTZ,
    created_by         VARCHAR(100)  NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_maintenance_plans_lead CHECK (lead_days < interval_days)
);
CREATE INDEX ix_maintenance_plans_asset ON maintenance_plans (asset_id);
CREATE INDEX ix_maintenance_plans_due ON maintenance_plans (next_due_date) WHERE active;

ALTER TABLE work_orders ADD COLUMN plan_id UUID REFERENCES maintenance_plans (id);

-- A plan never has two jobs open at the same time, even with several instances of the planner running.
CREATE UNIQUE INDEX ux_work_orders_open_plan ON work_orders (plan_id)
    WHERE plan_id IS NOT NULL AND status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD');
CREATE INDEX ix_work_orders_plan ON work_orders (plan_id, created_at DESC) WHERE plan_id IS NOT NULL;
