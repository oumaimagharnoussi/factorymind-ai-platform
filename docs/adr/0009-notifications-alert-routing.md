# ADR 0009: Notifications and alert routing

- Status: accepted
- Date: 2026-11-04

## Context

The platform detects a lot: debounced sensor alerts (ADR 0004), urgent work orders, machines the model rates
HIGH risk (ADR 0005), and 14 Prometheus rules about the platform itself (ADR 0008). All of it was visible only to
someone who had the right screen open. A night-shift engineer was not told that M-102 turned critical, and nobody
was told that alert-service had been down for ten minutes.

We need to tell the right people, on the channel they read, without flooding them:

- operators on the floor see sensor alerts in the app; engineers and admins see everything;
- the on-call maintenance mailbox (and its Teams channel) gets critical events only;
- reliability engineers get the AI failure warnings; IT gets the platform alerts;
- who gets what changes over time and must not need a deployment;
- one flapping sensor must not send twenty e-mails, and a mail server that is down must not lose messages.

## Decision

### One notification-service, fed by Kafka and Alertmanager

A new Spring Boot service, `notification-service` (port 8089, database `notification_db`), owns the question
"who should hear about this, and how". It has no topic of its own; it reads:

| Input | What becomes a notification | Severity |
|---|---|---|
| `factorymind.alert.alerts` | RAISED, ESCALATED, RETRIGGERED (not acknowledged / cleared / resolved: the board shows those) | the alert's |
| `factorymind.maintenance.work-orders` | CREATED, and STATUS_CHANGED to COMPLETED | URGENT = critical, HIGH = warning, else info; completed = info |
| `factorymind.ai.risk-levels` | a machine turning HIGH (not while it stays HIGH) | warning |
| Alertmanager webhook `POST /api/notifications/alertmanager` | every firing and resolved Prometheus alert | the rule's `severity` label; resolved = info |

Each input is turned into one internal type, `PlantEvent`, so routing and delivery know nothing about where an
event came from. Unknown fields and enum values in the upstream JSON are read as strings: a new work-order status
in maintenance-service does not break this consumer.

**Prometheus alerts go through Alertmanager to the same service** instead of Alertmanager sending e-mails itself.
Alertmanager keeps what it is good at (grouping by `alertname` and `service`, inhibition: a critical alert on a
service hides its warnings, repeat interval), and notification-service keeps one set of rules, one set of channels
and one delivery log for plant and platform events. The price: if notification-service is down, platform alerts
cannot go out through it. `ServiceDown{service="notification-service"}` and `AlertmanagerCannotNotify` still show
in Prometheus, Alertmanager and Grafana; a production setup would add a second, direct Alertmanager receiver
(e-mail) for those two alerts only.

Alertmanager has no Keycloak account: it sends a shared bearer token (`ALERTMANAGER_WEBHOOK_TOKEN`), compared in
constant time in a separate security filter chain for that one path. The compose entrypoint writes the token from
the environment to the file `alertmanager.yml` reads (`credentials_file`), so the secret lives in `.env` only.
The gateway does not route the webhook in a usable way: through it the caller would also need a user token.

### Routing rules are data

A rule says: events of this **source** (or every source), at or above this **severity**, go over this
**channel** (EMAIL, TEAMS, IN_APP) to this **recipient** (addresses / webhook URL / role), with an optional
**throttle** in minutes. Rules live in the database, admins edit them in the app (Notifications > Routing rules),
and Flyway seeds seven demo rules (operators: sensor alerts in the app; engineers and admins: everything in the
app; on-call: critical by e-mail; reliability: AI risk by e-mail; IT: platform by e-mail; Teams: disabled until
someone pastes a real webhook). "Send a test" on a rule sends a message at once, so a new address or webhook is
checked before it matters.

Recipients are validated per channel. A Teams recipient must be an `https://` URL on a public host name (no IP,
`localhost`, `.local`, `.internal`, user info), because the server calls it: anything else would let an admin
account make the server call internal addresses (SSRF). The webhook URL carries its own credentials, so the API
shows it in full to admins only, and the delivery log shows only its host; error messages from Teams are
rewritten so they never contain the URL.

### Idempotent intake, outbox for delivery

`ingest(event)` runs in one transaction:

1. If a notification with the same **event key** exists, stop. Keys are built from the source's own ids
   (`alert:<alertId>:<eventType>:<occurredAt>`, `work-order:<id>:CREATED`, `platform:<fingerprint>:firing:<startsAt>`),
   so a Kafka redelivery or Alertmanager's repeat every 4 h is stored and sent once. A unique constraint backs
   the check if two instances race.
2. Evaluate the enabled rules; the IN_APP ones give the notification's audience (roles).
3. Store the notification and one **delivery** row per EMAIL / TEAMS rule: `PENDING`, or `SUPPRESSED` when the
   rule already has a message about the same machine, source and severity inside its throttle window.

Sending happens later, in `DeliveryDispatcher`, every 2 s: it locks due rows with `FOR UPDATE SKIP LOCKED`
(several instances share the work and never send the same row at the same time), sends, and marks each one
`SENT`, or schedules the next attempt with exponential backoff (15 s, 30 s, 1 min... capped at 15 min). After 6
attempts the delivery is `FAILED`; it shows in the delivery log, the `NotificationDeliveriesFailing` alert fires,
and an admin can send it again. This is the **transactional outbox** pattern: the Kafka offset is committed only
after the notification and its deliveries are, and a slow SMTP server never blocks a Kafka listener.

Delivery is at least once: if the process dies after the mail server accepted a message but before the commit,
that message is sent again. For notifications a rare duplicate is better than a lost critical alert.

### Channels

- **E-mail** with Spring Mail, HTML (every value escaped: titles carry text from other services) plus a plain
  text part. Development uses **Mailpit** (http://localhost:8025): nothing leaves the machine, every message is
  inspectable. Production sets `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` (STARTTLS on 587 by default).
- **Microsoft Teams** through a channel webhook created with Teams Workflows ("When a Teams webhook request is
  received"), payload: an Adaptive Card with the severity color, the facts and an "Open in FactoryMind" button.
  The legacy Office 365 connectors are retired, so we do not use their MessageCard format.
- **In-app**: the feed `GET /api/notifications` (what the caller's roles may see, newest first), read state per
  user (`notification_read`, written with `insert ... on conflict do nothing`), and a Server-Sent Events stream
  that pushes each new notification to the browsers whose user has one of its audience roles. The Angular bell
  shows the unread count, the latest eight, and a toast for critical ones.

### Machine names: OAuth2 client credentials

Messages must say "M-102 Hydraulic press", not a UUID, but a Kafka event carries no user token to call
asset-service with. notification-service therefore has its **own Keycloak client** (`notification-service`,
confidential, service account with the OPERATOR role, enough to read machines and nothing more) and gets a token
with the client-credentials grant, through Spring Security's `OAuth2ClientHttpRequestInterceptor` and an
`AuthorizedClientServiceOAuth2AuthorizedClientManager` (works outside any HTTP request). Spring caches the token
and renews it before it expires; the machine list is cached 5 minutes. If asset-service does not answer, messages
use a short id rather than failing.

### Observability

`factorymind_notifications_events_total{source,severity}`, `..._deliveries_total{channel,outcome}`,
`..._send_seconds{channel}`, `..._deliveries_pending`, `..._deliveries_failed`; a Notifications row on the Plant
operations dashboard; three new rules (`NotificationDeliveriesFailing`, `NotificationBacklog`,
`AlertmanagerCannotNotify`). Kafka listener observation is on, as in the other services, so the trace of a
reading continues from alert-service and maintenance-service into notification-service.

## Consequences

- One place answers "who was told, when, how, and did it work" (the delivery log), for plant and platform events.
- Routing changes without a deployment; a wrong rule is one click away from a test message.
- Kafka redeliveries, Alertmanager repeats and flapping sensors do not turn into duplicate messages.
- New moving parts: a database, Mailpit and Alertmanager containers, a Keycloak client secret, a webhook token.
- Notifications older than 30 days are deleted every night (`NOTIFICATION_RETENTION`), with their deliveries.
- Not done: per-user preferences and quiet hours, escalation when nobody acknowledges (PagerDuty-style on-call),
  SMS. Rules target roles and shared mailboxes, which fits a plant with shifts.
