-- One database per service: a service never reads another service's tables.
-- analytics_db is the warehouse: analytics-service reads the other databases (read-only) to load it.
-- Run by the db-init container on every start; each line only creates a database that is missing,
-- so a Postgres volume from an earlier phase gets the new databases too.
SELECT 'CREATE DATABASE asset_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'asset_db')\gexec
-- Keycloak users and sessions (production only; dev Keycloak keeps them in the container).
SELECT 'CREATE DATABASE keycloak_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec
SELECT 'CREATE DATABASE telemetry_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'telemetry_db')\gexec
SELECT 'CREATE DATABASE alert_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'alert_db')\gexec
SELECT 'CREATE DATABASE maintenance_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'maintenance_db')\gexec
SELECT 'CREATE DATABASE inventory_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'inventory_db')\gexec
SELECT 'CREATE DATABASE insights_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'insights_db')\gexec
SELECT 'CREATE DATABASE analytics_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'analytics_db')\gexec
