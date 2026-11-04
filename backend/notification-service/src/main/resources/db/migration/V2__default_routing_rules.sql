-- Starting rules for the demo. Admins change them in the app (Notifications > Routing rules).
-- E-mail addresses are demo mailboxes: Mailpit catches every message (http://localhost:8025).
INSERT INTO routing_rule (id, name, source, min_severity, channel, recipient, throttle_minutes, enabled,
                          created_at, updated_at, updated_by) VALUES
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a01', 'Operators: sensor alerts in the app', 'ALERT', 'WARNING', 'IN_APP',
     'OPERATOR', 0, true, now(), now(), 'system'),
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a02', 'Engineers: every plant event in the app', NULL, 'INFO', 'IN_APP',
     'ENGINEER', 0, true, now(), now(), 'system'),
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a03', 'Admins: every event in the app', NULL, 'INFO', 'IN_APP',
     'ADMIN', 0, true, now(), now(), 'system'),
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a04', 'On-call maintenance: critical events by e-mail', NULL, 'CRITICAL', 'EMAIL',
     'oncall@factorymind.local', 10, true, now(), now(), 'system'),
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a05', 'Reliability engineers: AI failure risk by e-mail', 'AI_RISK', 'WARNING', 'EMAIL',
     'reliability@factorymind.local', 60, true, now(), now(), 'system'),
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a06', 'IT operations: platform alerts by e-mail', 'PLATFORM', 'WARNING', 'EMAIL',
     'it-ops@factorymind.local', 30, true, now(), now(), 'system'),
    -- Disabled until an admin pastes the webhook of a real Teams channel and enables it.
    ('0b6f6a52-3c39-4d0e-9a51-6d3c4f6f0a07', 'Maintenance Teams channel: critical events', NULL, 'CRITICAL', 'TEAMS',
     'https://example.webhook.office.com/replace-with-your-teams-workflow-url', 10, false, now(), now(), 'system');
