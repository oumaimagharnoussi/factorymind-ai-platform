# FactoryMind AI — Smart Industrial Operations Platform

FactoryMind AI watches factory machines, detects abnormal behaviour and helps maintenance teams act before a
breakdown stops production. It is built as event-driven Spring Boot microservices with an Angular front end,
a Python ML service and Power BI analytics.

> **Demo project.** All factories, machines and sensor data are simulated. Thresholds in the demo data are
> illustrative, not industrial limits.

![Dashboard](docs/demo/dashboard.png)

## What works today (phase 1)

| Area | Status |
|---|---|
| Factories, production lines, machines, sensor alarm limits (CRUD, search, filters, paging) | ✅ |
| Machine status (OPERATIONAL / WARNING / STOPPED / MAINTENANCE) and fleet summary | ✅ |
| API gateway in front of every service | ✅ |
| Angular dashboard, machine list, machine details, new machine form, factories | ✅ |
| Flyway migrations, demo data, RFC 9457 error responses, optimistic locking | ✅ |
| Unit, web-slice and Testcontainers integration tests, GitHub Actions CI | ✅ |
| Sensor simulator, Kafka, telemetry, live charts | Weeks 5–7 |
| Work orders, preventive plans, spare parts | Weeks 8–9 |
| Anomaly detection and failure-risk model (Python, scikit-learn) | Weeks 10–11 |
| Maintenance copilot (Spring AI + RAG), Power BI reports | Weeks 12–13 |
| Keycloak / OAuth2 roles, observability | Weeks 3–4, 14–15 |

## Architecture

```
Angular (4200) ──► API Gateway (8080) ──► asset-service (8081) ──► PostgreSQL asset_db
                         │
                         └──► (next) telemetry, maintenance, inventory, alert, ai-insights
```

Each service owns its database; services talk over REST for queries and Kafka for events (from phase 2).
Decisions are recorded in [`docs/adr`](docs/adr).

## Tech stack

Java 25 · Spring Boot 4.0.9 · Spring Cloud 2025.1.4 (Gateway) · Spring Data JPA · Flyway · PostgreSQL 17 ·
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

Open http://localhost:4200. Swagger UI for the asset service: http://localhost:8081/swagger-ui.html

### For development

```bash
docker compose up -d postgres

# Backend (JDK 25; with JDK 21 add -Djava.version=21)
cd backend
./mvnw -pl asset-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run    # in a second terminal

# Frontend (Node 22.22.3+ or 24)
cd frontend/factorymind-web
npm install
npm start                                 # proxies /api to the gateway on :8080
```

Start with an empty database instead of the demo data:
`FLYWAY_LOCATIONS=classpath:db/migration`.

### Tests

```bash
cd backend && ./mvnw verify                  # needs Docker for the Testcontainers test
cd frontend/factorymind-web && npx ng test --watch=false
```

## Deploy

`docker-compose.prod.yml` runs the published images behind Caddy (automatic HTTPS). `.github/workflows/deploy.yml`
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

Errors use RFC 9457 problem details, for example:

```json
{ "status": 409, "title": "Conflict", "detail": "Asset code 'M-104' is already used" }
```

## Repository layout

```
backend/          Maven multi-module: asset-service, api-gateway (more services added per phase)
frontend/         Angular app (factorymind-web)
ai/               Python ML services (phase 3)
analytics/        Warehouse, ETL and Power BI (phase 4)
infrastructure/   Database init scripts, later Kafka, Keycloak, Prometheus, Grafana
docs/             ADRs, architecture notes, screenshots
```
