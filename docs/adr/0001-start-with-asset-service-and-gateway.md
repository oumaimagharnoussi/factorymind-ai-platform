# ADR 0001: Start with the asset service, a gateway and one database per service

- Status: accepted
- Date: 2026-10-25

## Context

FactoryMind AI will eventually have around ten services (telemetry, maintenance, inventory, alerts, AI insights...).
Building them all at once would produce many empty services and no working demo.

## Decision

Phase 1 delivers one complete vertical slice:

- `asset-service` owns factories, production lines, machines and sensor alarm limits, in its own `asset_db`.
- `api-gateway` (Spring Cloud Gateway) is the only entry point for the Angular app, so CORS, rate limiting and
  token validation live in one place later.
- No service discovery or config server yet: with Docker Compose, service names are already stable DNS names.
- Spring Boot 4.0.9 with Spring Cloud 2025.1.4, because the 2025.1 release train supports Boot 4.0.x and not 4.1.x.
- Schema changes go through Flyway (`ddl-auto: validate`), so the database is never changed implicitly by Hibernate.

## Consequences

- Every other service will refer to a machine by its `id` / `assetCode`, never by joining `asset_db` tables.
- The machine `status` is set manually for now; in phase 2 the alert service will change it from telemetry events.
- Adding a service means adding a route in the gateway and a database in `infrastructure/postgres/init-databases.sql`.
