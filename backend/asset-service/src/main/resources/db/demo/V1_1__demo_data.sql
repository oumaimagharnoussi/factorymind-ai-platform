-- Demo data only. These numbers are illustrative, not industrial limits valid for real machines.

INSERT INTO factories (id, name, location, timezone, created_at, updated_at) VALUES
    ('11111111-0000-0000-0000-000000000001', 'Sfax Plant', 'Sfax, Tunisia', 'Africa/Tunis', now(), now()),
    ('11111111-0000-0000-0000-000000000002', 'Bizerte Plant', 'Bizerte, Tunisia', 'Africa/Tunis', now(), now());

INSERT INTO production_lines (id, factory_id, name, status, created_at, updated_at) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 'Assembly Line A', 'RUNNING', now(), now()),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000001', 'Packaging Line B', 'RUNNING', now(), now()),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002', 'Machining Line C', 'IDLE', now(), now());

INSERT INTO assets (id, asset_code, name, line_id, manufacturer, model, criticality, installed_at, status, created_at, updated_at) VALUES
    ('33333333-0000-0000-0000-000000000101', 'M-101', 'Main Conveyor Motor', '22222222-0000-0000-0000-000000000001', 'Siemens', '1LE1 IE3', 'HIGH', '2021-03-15', 'OPERATIONAL', now(), now()),
    ('33333333-0000-0000-0000-000000000102', 'M-102', 'Hydraulic Press', '22222222-0000-0000-0000-000000000001', 'Schuler', 'HP-400', 'CRITICAL', '2019-09-01', 'OPERATIONAL', now(), now()),
    ('33333333-0000-0000-0000-000000000103', 'M-103', 'Air Compressor', '22222222-0000-0000-0000-000000000001', 'Atlas Copco', 'GA 37', 'MEDIUM', '2022-01-10', 'MAINTENANCE', now(), now()),
    ('33333333-0000-0000-0000-000000000104', 'M-104', 'Electric Motor Drive', '22222222-0000-0000-0000-000000000002', 'ABB', 'M3BP 250', 'HIGH', '2020-06-20', 'WARNING', now(), now()),
    ('33333333-0000-0000-0000-000000000105', 'M-105', 'Packaging Robot', '22222222-0000-0000-0000-000000000002', 'FANUC', 'M-20iD', 'MEDIUM', '2023-04-05', 'OPERATIONAL', now(), now()),
    ('33333333-0000-0000-0000-000000000106', 'M-106', 'CNC Milling Center', '22222222-0000-0000-0000-000000000003', 'DMG MORI', 'DMU 50', 'CRITICAL', '2018-11-12', 'STOPPED', now(), now()),
    ('33333333-0000-0000-0000-000000000107', 'M-107', 'Coolant Pump', '22222222-0000-0000-0000-000000000003', 'Grundfos', 'CR 10', 'LOW', '2022-08-30', 'OPERATIONAL', now(), now());

INSERT INTO sensor_definitions (id, asset_id, sensor_type, unit, warning_threshold, critical_threshold, created_at, updated_at)
SELECT gen_random_uuid(), a.id, s.sensor_type, s.unit, s.warning, s.critical, now(), now()
FROM assets a
CROSS JOIN (VALUES
    ('TEMPERATURE', '°C', 75.0, 90.0),
    ('VIBRATION', 'mm/s', 4.5, 7.1)
) AS s (sensor_type, unit, warning, critical);

INSERT INTO sensor_definitions (id, asset_id, sensor_type, unit, warning_threshold, critical_threshold, created_at, updated_at) VALUES
    (gen_random_uuid(), '33333333-0000-0000-0000-000000000102', 'PRESSURE', 'bar', 180.0, 210.0, now(), now()),
    (gen_random_uuid(), '33333333-0000-0000-0000-000000000104', 'ROTATION_SPEED', 'rpm', 1550.0, 1650.0, now(), now());
