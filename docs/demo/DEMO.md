# Demo script (10 minutes)

A guided tour of FactoryMind for a review or an interview. It follows one machine failing, from the first sign
in the data to the repair, then shows how the platform is observed and tested. Every step works with
`docker compose up -d --build` and the simulated data; nothing has to be prepared by hand.

![Tour of the app](tour.gif)

## Before you start (2 minutes, before the audience joins)

1. `docker compose up -d --build`, wait until `docker compose ps` shows the services running (first build: several
   minutes).
2. Open these tabs:
   - http://localhost:4200, signed in as `engineer` / `engineer`;
   - http://localhost:8025 (Mailpit);
   - http://localhost:3000 (Grafana), signed in as `admin` / `admin`;
   - http://localhost:8085 (Kafka UI).
3. Optional, for the copilot: `AI_CHAT_PROVIDER=anthropic` and `ANTHROPIC_API_KEY` in `.env`, or Ollama (see the
   README). Without it the copilot still finds and cites the right manual passages.

**What the simulator does.** Two machines, **M-102 Hydraulic Press** and **M-104 Electric Motor Drive**, degrade
slowly and then recover, on a 6-minute cycle (`SIMULATOR_DEGRADE_CYCLE`). The other machines stay healthy. So
within any 6 minutes you see the whole story at least once.

## 1. The plant at a glance (1 minute)

**Dashboard.** Seven machines, how many can produce, open alerts, open work orders, MTTR, and machines at high
failure risk.

> "Everything here comes from separate services: assets, telemetry, alerts, maintenance, AI. The gateway checks
> the Keycloak token, and each service checks it again and applies the roles."

## 2. Live data (1 minute)

**Live monitoring.** Every sensor arrives through Kafka every 3 seconds and is checked against its limits. Wait for
M-102's pressure, or M-104's temperature, to climb toward the orange limit.

> "A reading goes simulator → Kafka `telemetry.readings` → telemetry-service, which stores it and pushes it to the
> browser with Server-Sent Events. A status change goes out as its own event, `telemetry.sensor-status`."

## 3. The model sees it first (1 minute)

**AI risk center.** While the value is still below the warning limit, the scikit-learn model already rates the
machine **HIGH**: it looks at the trend, not only at the current value. The reasons are written in plain words
("trend reaches the critical limit in about 25 min").

**Work orders.** An **INSPECTION** work order was opened by `ai-insights`, before any alert.

> "This is the business value: a technician is sent before the machine stops, not after."

## 4. The alarm and the repair (2 minutes)

**Alerts.** About a minute later the sensor crosses the critical limit. After 15 seconds outside the limit an
alert is raised (a short spike does not count). It is critical, so maintenance-service opens an **URGENT**
corrective work order automatically and the alert shows it.

**Bell (top right) and Mailpit.** The critical alert and the urgent work order are in the bell. The on-call
mailbox received the e-mails, sent by notification-service according to the routing rules
(Notifications > Routing rules, as `admin`).

**Work order detail.** Assign it, set it to In progress, then book a spare part. The stock goes down in Spare parts.
Complete the work order: the alert is resolved.

> "Five services took part, and none of them called another one directly to make it happen. They react to Kafka
> events: alert → work order → notification → alert resolved."

## 5. The numbers for management (1 minute)

**Analytics.** Availability, MTBF, MTTR, maintenance cost, and how well the model predicts failures (precision
and recall), over 30 to 180 days. The same warehouse feeds the Power BI reports in `analytics/powerbi`.

## 6. How it is run (2 minutes)

Grafana (http://localhost:3000):

- **FactoryMind - Plant operations**: the same story as metrics. Critical alerts, how fast they are acknowledged,
  work orders by origin (ALERT / AI_PREDICTION / USER).
- **Explore > Tempo > Search**, service `alert-service`: one trace follows the reading through Kafka into
  alert-service, maintenance-service and notification-service.
- **Explore > Loki**: `{service="notification-service"} | trace_id != ""`. Open a line and click **View trace**.
  Logs, traces and metrics are linked.
- **FactoryMind - Logs**: errors per service. To show an alert on logs, send `hello` (not JSON) to
  `factorymind.alert.alerts` in Kafka UI (Topics > Produce Message). The consumers log it as dead-lettered, and
  2 minutes later Mailpit has "… sent Kafka messages to a dead-letter topic".

## 7. How it is kept safe to change (1 minute)

- `contracts/`: the JSON Schemas of the six Kafka topics and of the model's API. Every producer and consumer
  checks them in its tests. Removing `URGENT` from the work-order priorities, for example, fails the
  maintenance-service build.
- CI on every push: backend (unit and integration tests with Testcontainers: Postgres, Kafka, SMTP), frontend,
  Python model, Prometheus/Loki/Alloy configuration, Docker images.

## Questions that usually come up

| Question | Short answer | Where |
|---|---|---|
| Why Kafka and not REST between services? | Events decouple producers from consumers. The alert service does not know who reacts to an alert, and a service that is down catches up from its offset. Queries stay on REST. | [ADR 0003](../adr/0003-kafka-telemetry.md) |
| What happens if a message is bad? | Two retries, then the record is copied to `<topic>-dlt`, logged at ERROR and alerted on. The partition keeps moving. | [ADR 0010](../adr/0010-centralized-logs.md) |
| How do you avoid duplicate e-mails? | A unique event key per notification (idempotency), a transactional outbox with retries, and a throttle per rule. | [ADR 0009](../adr/0009-notifications-alert-routing.md) |
| How are services secured? | Keycloak (OIDC with PKCE for the browser, JWT checked in the gateway and again in each service, roles), and client credentials for service-to-service calls. | [ADR 0002](../adr/0002-keycloak-security.md) |
| How good is the model? | Its model card has the test metrics. In the app, the analytics page compares predictions with what really happened. | [ADR 0005](../adr/0005-ai-insights-ml.md) |
| How do services evolve without breaking each other? | Shared contracts tested on both sides: add fields, never change them in place. | [contracts/README.md](../../contracts/README.md) |
| What would you do next? | Kubernetes with Helm and autoscaling of the telemetry consumers, Schema Registry with Avro if the number of teams grows, preventive maintenance plans, retraining the model on real failure history. | |
