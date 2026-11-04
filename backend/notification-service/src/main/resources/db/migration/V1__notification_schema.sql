-- notification-service: routing rules, the notifications they produced, and the outbox of messages to send.

CREATE TABLE routing_rule (
    id               uuid PRIMARY KEY,
    name             varchar(120)  NOT NULL,
    source           varchar(20),                    -- null = every source
    min_severity     varchar(10)   NOT NULL,
    channel          varchar(10)   NOT NULL,
    recipient        varchar(1000) NOT NULL,
    throttle_minutes integer       NOT NULL DEFAULT 0,
    enabled          boolean       NOT NULL DEFAULT true,
    version          bigint        NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT ck_rule_source CHECK (source IN ('ALERT', 'WORK_ORDER', 'AI_RISK', 'PLATFORM')),
    CONSTRAINT ck_rule_severity CHECK (min_severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_rule_channel CHECK (channel IN ('EMAIL', 'TEAMS', 'IN_APP')),
    CONSTRAINT ck_rule_throttle CHECK (throttle_minutes BETWEEN 0 AND 1440)
);

CREATE TABLE notification (
    id          uuid PRIMARY KEY,
    -- Kafka redelivers and Alertmanager repeats: one row per event, whatever arrives twice.
    event_key   varchar(200)  NOT NULL,
    source      varchar(20)   NOT NULL,
    severity    varchar(10)   NOT NULL,
    asset_id    uuid,
    asset_label varchar(200),
    title       varchar(200)  NOT NULL,
    message     varchar(2000) NOT NULL,
    link        varchar(500),
    occurred_at timestamptz   NOT NULL,
    created_at  timestamptz   NOT NULL,
    CONSTRAINT uk_notification_event_key UNIQUE (event_key)
);
CREATE INDEX ix_notification_created ON notification (created_at DESC);

-- Roles that see the notification in the app (from the matching IN_APP rules).
CREATE TABLE notification_audience (
    notification_id uuid        NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    role            varchar(20) NOT NULL,
    PRIMARY KEY (notification_id, role)
);
CREATE INDEX ix_audience_role ON notification_audience (role, notification_id);

-- Read state per user (the bell's unread count).
CREATE TABLE notification_read (
    notification_id uuid         NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    username        varchar(100) NOT NULL,
    read_at         timestamptz  NOT NULL,
    PRIMARY KEY (notification_id, username)
);
CREATE INDEX ix_read_username ON notification_read (username);

-- Outbox: one e-mail or Teams message to send, retried until SENT or FAILED.
CREATE TABLE delivery (
    id              uuid PRIMARY KEY,
    notification_id uuid          NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    rule_id         uuid,         -- no foreign key: deleting a rule keeps the history of what it sent
    rule_name       varchar(120)  NOT NULL,
    channel         varchar(10)   NOT NULL,
    recipient       varchar(1000) NOT NULL,
    throttle_key    varchar(300),
    status          varchar(12)   NOT NULL,
    attempts        integer       NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_error      varchar(1000),
    created_at      timestamptz   NOT NULL,
    sent_at         timestamptz,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SUPPRESSED')),
    CONSTRAINT ck_delivery_channel CHECK (channel IN ('EMAIL', 'TEAMS'))
);
-- The dispatcher's query: due pending deliveries, oldest first.
CREATE INDEX ix_delivery_due ON delivery (next_attempt_at) WHERE status = 'PENDING';
-- The throttle check: recent deliveries of a rule about the same thing.
CREATE INDEX ix_delivery_throttle ON delivery (rule_id, throttle_key, created_at DESC);
CREATE INDEX ix_delivery_notification ON delivery (notification_id);
CREATE INDEX ix_delivery_created ON delivery (created_at DESC);
