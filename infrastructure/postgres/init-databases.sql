-- One database per service: a service never reads another service's tables.
CREATE DATABASE asset_db;
-- Added in later phases:
-- CREATE DATABASE telemetry_db;
-- CREATE DATABASE maintenance_db;
-- CREATE DATABASE inventory_db;
-- CREATE DATABASE alert_db;
