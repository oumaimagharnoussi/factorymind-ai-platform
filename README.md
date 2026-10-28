# FactoryMind AI — Smart Industrial Operations Platform

FactoryMind AI watches factory machines, detects abnormal behaviour and helps maintenance teams act before a
breakdown stops production. It is built as event-driven Spring Boot microservices with an Angular front end,
a Python ML service and Power BI analytics.

> **Demo project.** All factories, machines and sensor data are simulated. Thresholds in the demo data are
> illustrative, not industrial limits.

![Dashboard](docs/demo/dashboard.png)

![Live monitoring](docs/demo/monitoring.png)

![Alerts](docs/demo/alerts.png)

![Work order](docs/demo/work-order.png)

## What works today (phases 1–4)

| Area | Status |
|---|---|
| Factories, production lines, machines, sensor alarm limits (CRUD, search, filters, paging) | ✅ |
| Machine status (OPERATIONAL / WARNING / STOPPED / MAINTENANCE) and fleet summary | ✅ |
| API gateway in front of every service | ✅ |
| Angular dashboard, machine list, machine details, new machine form, factories | ✅ |
| Flyway migrations, demo data, RFC 9457 error responses, optimistic locking | ✅ |
| Unit, web-slice and Testcontainers integration tests, GitHub Actions CI | ✅ |
| Keycloak login (OIDC + PKCE), JWT validation in the gateway and the asset service, ADMIN / ENGINEER / OPERATOR roles | ✅ |
| Kafka (KRaft): sensor limits shared by asset-service, readings, status changes, dead-letter topics | ✅ |
| Telemetry service: threshold checks, history, latest values, live stream (SSE), sensor simulator | ✅ |
| Live monitoring screen: every sensor with sparkline, limits and a feed of status changes | ✅ |
| Alert service: debounced alerts from sensor status changes, acknowledge / resolve, live stream | ✅ |
| Maintenance service: work orders (auto-created from critical alerts), status workflow, parts used, history, MTTR | ✅ |
| Inventory service: spare parts, stock movements ledger, receipts, stock counts, low-stock view | ✅ |
| Preventive maintenance plans | Later |
| Anomaly detection and failure-risk model (Python, scikit-learn) | Weeks 10–11 |
| Maintenance copilot (Spring AI + RAG), Power BI reports | Weeks 12–13 |
| Observability (Prometheus, Grafana) | Weeks 14–15 |

## Architecture

```
                 login (PKCE)
Angular (4200) ─────────────────► Keycloak (8180/auth, realm factorymind)
      │ Bearer token, live streams (SSE)       ▲ signing keys (JWKS)
      ▼                                        │
API Gateway (8080) ─┬─► asset-service (8081) ──────► PostgreSQL asset_db
                    ├─► telemetry-service (8082) ──► PostgreSQL telemetry_db
                    ├─► alert-service (8083) ──────► PostgreSQL alert_db
                    ├─► maintenance-service (8084) ► PostgreSQL maintenance_db
                    │        │ REST: issue / return parts (token relayed)
                    └─► inventory-service (8086) ──► PostgreSQL inventory_db

Kafka (9092)
  asset.sensor-definitions   asset ──► telemetry
  telemetry.readings         simulator / plant gateways ──► telemetry
  telemetry.sensor-status    telemetry ──► alert (debounce)
  alert.alerts               alert ──► maintenance (critical alert opens a work order)
  maintenance.work-orders    maintenance ──► alert (acknowledge, resolve when completed)
```

Each service owns its database; services talk over REST for queries and Kafka for events.
A reading travels: simulator or `POST /api/telemetry/readings` → `telemetry.readings` → telemetry-service
(limits check, stored, latest state) → live stream to the browser, and `telemetry.sensor-status` when a sensor
changes level. See [ADR 0003](docs/adr/0003-kafka-telemetry.md).
A status change then becomes an alert once it lasts 15 s (and clears after 30 s back to normal); a critical
alert opens an urgent work order, or joins the open one of the same machine; the technician books spare parts
on it, which takes them out of stock. See [ADR 0004](docs/adr/0004-alerts-maintenance-inventory.md).
Decisions are recorded in [`docs/adr`](docs/adr).

## Security

Keycloak issues the tokens; the Angular app signs in with the authorization code flow and PKCE (`keycloak-js`)
and sends the access token to `/api`. The gateway rejects any call without a valid token and a FactoryMind role,
then forwards the token; the asset service checks it again and applies the role rules:

| Role | Can do |
|---|---|
| OPERATOR | Read factories, lines, machines and sensors; report a machine's status |
| ENGINEER | Everything an operator does, plus create and edit machines and sensor limits |
| ADMIN | Everything, including factories, production lines and deletes |

Every role can watch live and historical readings; pushing readings over HTTP needs ENGINEER or ADMIN.

| Area | OPERATOR | ENGINEER | ADMIN |
|---|---|---|---|
| Alerts | Read, acknowledge | + resolve | Same as engineer |
| Work orders | Read, create | + edit, change status, book parts | Same as engineer |
| Spare parts | Read | + create / edit parts, receive deliveries | + stock counts, delete parts |

Demo accounts (realm import in `infrastructure/keycloak/factorymind-realm.json`; change them before any real use):

| User | Password | Role |
|---|---|---|
| `admin` | `admin` | ADMIN |
| `engineer` | `engineer` | ENGINEER |
| `operator` | `operator` | OPERATOR |

Keycloak admin console: http://localhost:8180/auth/admin (`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`, default
`admin` / `admin`). See [ADR 0002](docs/adr/0002-keycloak-security.md).

## Tech stack

Java 25 · Spring Boot 4.0.9 · Spring Cloud 2025.1.4 (Gateway) · Spring for Apache Kafka · Apache Kafka 4.3 (KRaft) ·
Spring Data JPA · Flyway · PostgreSQL 17 · Server-Sent Events ·
Angular 22 (standalone components, signals, `rxResource`, reactive forms, Angular Material) · Vitest ·
JUnit 5 · Mockito · Testcontainers · Docker Compose · GitHub Actions.

Spring Cloud 2025.1.x supports Spring Boot 4.0.x, not 4.1.x, so the project stays on Boot 4.0.9 until a
compatible Spring Cloud release train is available.

## Run it

### Everything in Docker

```bash
cp .env.example .env
docker compose up --build
```

Open http://localhost:4200 and sign in with one of the demo accounts. **Live monitoring** shows the simulated
sensors; M-102 and M-104 slowly degrade to critical and recover every 6 minutes. A few seconds after they
turn critical, **Alerts** shows the alarms and **Work orders** gets an urgent order for each machine.

| URL | What |
|---|---|
| http://localhost:8085 | Kafka UI: topics, messages, consumer lag, dead-letter topics |
| http://localhost:8081/swagger-ui.html | asset-service API |
| http://localhost:8082/swagger-ui.html | telemetry-service API |
| http://localhost:8083/swagger-ui.html | alert-service API |
| http://localhost:8084/swagger-ui.html | maintenance-service API |
| http://localhost:8086/swagger-ui.html | inventory-service API |

Already ran an earlier phase? `docker compose up --build` is enough: the `db-init` container creates
the missing databases (`telemetry_db`, `alert_db`, `maintenance_db`, `inventory_db`) in the existing Postgres volume.

### For development

```bash
docker compose up -d postgres db-init kafka kafka-ui keycloak

# Backend (JDK 25; with JDK 21 add -Djava.version=21), one terminal each
cd backend
./mvnw -pl asset-service spring-boot:run
SIMULATOR_ENABLED=true ./mvnw -pl telemetry-service spring-boot:run
./mvnw -pl alert-service spring-boot:run
./mvnw -pl inventory-service spring-boot:run
./mvnw -pl maintenance-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run

# Frontend (Node 22.22.3+ or 24)
cd frontend/factorymind-web
npm install
npm start                                 # proxies /api to the gateway on :8080
```

Start with an empty database instead of the demo data:
`FLYWAY_LOCATIONS=classpath:db/migration`.

Signed-in call from a terminal: the web client has no password grant, so take the token from the browser
(DevTools > Network > any `/api` call > `Authorization` header) and pass it as `-H "Authorization: Bearer <token>"`.

### Tests

```bash
cd backend && ./mvnw verify                  # needs Docker for the Testcontainers tests (PostgreSQL, Kafka)
cd frontend/factorymind-web && npx ng test --watch=false
```

## Deploy

`docker-compose.prod.yml` runs the published images behind Caddy (automatic HTTPS), with Keycloak at
`https://$DOMAIN/auth` stored in `keycloak_db`, and a private Kafka broker (set `KAFKA_CLUSTER_ID` once in `.env`).
The `db-init` container creates any missing service database on each start. `.github/workflows/deploy.yml`
tests, builds and pushes the images to GHCR on every push to `main`, then updates the server over SSH once the
`SERVER_HOST`, `SERVER_USER`, `SERVER_SSH_KEY` secrets and the `DEPLOY_ENABLED=true` variable are set.

## API (asset-service, through the gateway)

| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/api/factories` | List / create factories |
| GET/PUT/DELETE | `/api/factories/{id}` | Read / update / delete (409 while lines exist) |
| GET/POST | `/api/production-lines?factoryId=` | List / create lines |
| GET/PUT/DELETE | `/api/production-lines/{id}` | Read / update / delete (409 while machines exist) |
| GET | `/api/assets?q=&status=&criticality=&factoryId=&lineId=&page=&size=` | Search machines |
| GET | `/api/assets/summary` | Counts by status and criticality |
| GET/POST | `/api/assets`, `/api/assets/{id}` | Create / read machines |
| PUT/DELETE | `/api/assets/{id}` | Update / delete a machine |
| PATCH | `/api/assets/{id}/status` | Change machine status |
| GET/POST/PUT/DELETE | `/api/assets/{id}/sensors[/{sensorId}]` | Sensor alarm limits |

## API (telemetry-service, through the gateway)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/telemetry/latest` | Latest value, status and limits of every sensor |
| GET | `/api/telemetry/readings?assetId=&sensorType=&minutes=15` | History, oldest first (max 1440 minutes, 5000 points) |
| GET | `/api/telemetry/stream` | Server-Sent Events: `reading` for each new value, `status` when a sensor changes level |
| POST | `/api/telemetry/readings` | Queue a reading on Kafka (`{"assetId", "sensorType", "value", "recordedAt"?}`), 202 |

## API (alert-service, through the gateway)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/alerts?status=&severity=&assetId=&sensorType=&active=&page=&size=` | Search alerts (`status` can repeat) |
| GET | `/api/alerts/summary` | Unresolved alerts: critical, warning, active, not acknowledged |
| GET | `/api/alerts/{id}` | One alert |
| GET | `/api/alerts/stream` | Server-Sent Events: `alert` on every change |
| POST | `/api/alerts/{id}/acknowledge` | Acknowledge |
| POST | `/api/alerts/{id}/resolve` | Resolve once the condition has cleared (`{"note"}`), 422 while still active |

## API (maintenance-service, through the gateway)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/maintenance/work-orders?q=&status=&priority=&type=&assetId=&alertId=&page=&size=` | Search work orders |
| GET | `/api/maintenance/summary` | Counts by status, open, overdue, urgent, MTTR, completed in 30 days |
| GET/POST | `/api/maintenance/work-orders`, `/api/maintenance/work-orders/{id}` | Create (optionally from an `alertId`) / read with parts and history |
| PUT | `/api/maintenance/work-orders/{id}` | Edit title, description, priority, assignee, due date |
| PATCH | `/api/maintenance/work-orders/{id}/status` | Move in the workflow (`{"status", "note"}`; a note is required to complete) |
| POST | `/api/maintenance/work-orders/{id}/parts` | Use a spare part (`{"partId", "quantity"}`), takes it out of stock |
| DELETE | `/api/maintenance/work-orders/{id}/parts/{lineId}` | Remove a part line and return it to stock |

## API (inventory-service, through the gateway)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/inventory/parts?q=&category=&lowStock=&page=&size=` | Search spare parts |
| GET | `/api/inventory/summary` | Parts, low stock, out of stock, stock value |
| GET/POST/PUT/DELETE | `/api/inventory/parts[/{id}]` | Spare part catalogue |
| GET | `/api/inventory/parts/{id}/movements`, `/api/inventory/movements?workOrderId=` | Stock ledger |
| POST | `/api/inventory/parts/{id}/receipts` | Receive a delivery (`{"quantity", "reference", "unitCost"?}`) |
| POST | `/api/inventory/parts/{id}/issues`, `/returns` | Used by maintenance-service, idempotent by `requestId` |
| POST | `/api/inventory/parts/{id}/adjustments` | Stock count (`{"countedQuantity", "note"}`), ADMIN |

Errors use RFC 9457 problem details, for example:

```json
{ "status": 409, "title": "Conflict", "detail": "Asset code 'M-104' is already used" }
```

## Repository layout

```
backend/          Maven multi-module: asset, telemetry, alert, maintenance, inventory services and api-gateway
frontend/         Angular app (factorymind-web)
ai/               Python ML services (phase 3)
analytics/        Warehouse, ETL and Power BI (phase 4)
infrastructure/   Database init script, Keycloak realm, Caddy; later Prometheus, Grafana
docs/             ADRs, architecture notes, screenshots
```
