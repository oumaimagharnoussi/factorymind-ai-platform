-- One database per service: a service never reads another service's tables.
CREATE DATABASE asset_db;
-- Keycloak users and sessions (production only; dev Keycloak keeps them in the container).
CREATE DATABASE keycloak_db;
-- Added in later phases:
-- CREATE DATABASE telemetry_db;
-- CREATE DATABASE maintenance_db;
-- CREATE DATABASE inventory_db;
-- CREATE DATABASE alert_db;
