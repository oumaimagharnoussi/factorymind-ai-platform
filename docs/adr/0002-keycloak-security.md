# ADR 0002: Keycloak for login, JWT checked at the gateway and in each service

- Status: accepted
- Date: 2026-10-26

## Context

Phase 1 left every endpoint open. A maintenance platform needs at least three kinds of users: operators who
watch the plant and report problems, engineers who register machines and alarm limits, and administrators.
We do not want to store passwords or write login screens ourselves.

## Decision

- Keycloak 26 is the identity provider. The `factorymind` realm (imported from
  `infrastructure/keycloak/factorymind-realm.json`) defines the realm roles `ADMIN`, `ENGINEER`, `OPERATOR`,
  a public client `factorymind-web` and three demo users.
- The Angular app uses the authorization code flow with PKCE through `keycloak-js`: no client secret in the
  browser, tokens refreshed before each API call, and only `/api/` requests get the `Authorization` header.
- `api-gateway` is an OAuth2 resource server: no valid token or no FactoryMind role means 401/403 before any
  service is reached. It forwards the token unchanged.
- `asset-service` validates the same token again and holds the role rules for its own endpoints. A service
  never trusts the network: it stays safe if someone calls it directly on port 8081.
- Keycloak runs under `/auth` with a fixed public hostname (`KC_HOSTNAME`), so the `iss` claim is the same
  whichever network asked for the token. Services check `iss` against that public URL and fetch signing keys
  from the internal Docker address (`KEYCLOAK_JWK_SET_URI`).
- Realm roles are read from the `realm_access.roles` claim and mapped to Spring `ROLE_*` authorities.

## Consequences

- Each new service adds `spring-boot-starter-security-oauth2-resource-server`, the two `KEYCLOAK_*` variables and
  its own `SecurityConfig`.
- The UI hides actions a role cannot perform, but the services remain the source of truth.
- In dev, Keycloak keeps its data inside the container; in production it uses `keycloak_db` in PostgreSQL.
- Later, service-to-service calls (for example the alert service updating a machine status) will use a
  confidential client with the client-credentials flow.
