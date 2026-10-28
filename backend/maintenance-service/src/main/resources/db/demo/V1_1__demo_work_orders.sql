-- Demo data only. Machine ids come from asset-service's demo data, part ids from inventory-service's.

INSERT INTO work_orders (id, code, asset_id, type, priority, status, title, description, assignee, due_date,
                         requested_by, started_at, completed_at, resolution, created_at, updated_at) VALUES
    ('55555555-0000-0000-0000-000000000001', 'WO-00001', '33333333-0000-0000-0000-000000000103', 'PREVENTIVE', 'MEDIUM',
     'IN_PROGRESS', '4,000-hour service: air filter and oil', 'Scheduled service of the GA 37 compressor.',
     'engineer', current_date, 'engineer', now() - interval '2 hours', NULL, NULL,
     now() - interval '1 day', now() - interval '2 hours'),
    ('55555555-0000-0000-0000-000000000002', 'WO-00002', '33333333-0000-0000-0000-000000000106', 'CORRECTIVE', 'HIGH',
     'OPEN', 'Spindle noise, machine stopped during cycle', 'Operator heard grinding from the spindle before the stop.',
     NULL, current_date + 1, 'operator', NULL, NULL, NULL,
     now() - interval '5 hours', now() - interval '5 hours'),
    ('55555555-0000-0000-0000-000000000003', 'WO-00003', '33333333-0000-0000-0000-000000000101', 'CORRECTIVE', 'MEDIUM',
     'COMPLETED', 'Bearing noise on conveyor motor', 'Rising vibration on the drive end.',
     'engineer', current_date - 10, 'operator', now() - interval '11 days', now() - interval '10 days 20 hours',
     'Drive-end and non-drive-end bearings worn. Replaced both, vibration back to 1.8 mm/s.',
     now() - interval '11 days 3 hours', now() - interval '10 days 20 hours'),
    ('55555555-0000-0000-0000-000000000004', 'WO-00004', '33333333-0000-0000-0000-000000000105', 'INSPECTION', 'LOW',
     'ON_HOLD', 'Gripper calibration check', 'Pick position drifts by about 1 mm.',
     'engineer', current_date + 7, 'engineer', now() - interval '2 days 4 hours', NULL, NULL,
     now() - interval '3 days', now() - interval '2 days');

SELECT setval('work_order_number_seq', 4);

INSERT INTO work_order_parts (id, work_order_id, part_id, part_number, part_name, unit, quantity, unit_cost, issued_by, issued_at) VALUES
    ('66666666-0000-0000-0000-000000000001', '55555555-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000004',
     'AC-2914-9306', 'Compressor air filter element', 'pcs', 1, 48.50, 'engineer', now() - interval '90 minutes'),
    ('66666666-0000-0000-0000-000000000002', '55555555-0000-0000-0000-000000000001', '44444444-0000-0000-0000-000000000005',
     'AC-ROTOR-XTEND', 'Compressor oil Roto-Xtend', 'L', 5, 22.00, 'engineer', now() - interval '80 minutes'),
    ('66666666-0000-0000-0000-000000000003', '55555555-0000-0000-0000-000000000003', '44444444-0000-0000-0000-000000000001',
     'SKF-6309-2Z', 'Deep groove ball bearing 6309-2Z', 'pcs', 2, 36.90, 'engineer', now() - interval '10 days 22 hours');

INSERT INTO work_order_activities (id, work_order_id, type, message, actor, occurred_at) VALUES
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000001', 'CREATED', 'Work order created', 'engineer', now() - interval '1 day'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000001', 'STATUS_CHANGED', 'OPEN → IN_PROGRESS', 'engineer', now() - interval '2 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000001', 'PART_USED', '1 pcs × AC-2914-9306 Compressor air filter element', 'engineer', now() - interval '90 minutes'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000001', 'PART_USED', '5 L × AC-ROTOR-XTEND Compressor oil Roto-Xtend', 'engineer', now() - interval '80 minutes'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000002', 'CREATED', 'Work order created', 'operator', now() - interval '5 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000003', 'CREATED', 'Work order created', 'operator', now() - interval '11 days 3 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000003', 'STATUS_CHANGED', 'OPEN → IN_PROGRESS', 'engineer', now() - interval '11 days'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000003', 'PART_USED', '2 pcs × SKF-6309-2Z Deep groove ball bearing 6309-2Z', 'engineer', now() - interval '10 days 22 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000003', 'STATUS_CHANGED', 'IN_PROGRESS → COMPLETED: Drive-end and non-drive-end bearings worn. Replaced both, vibration back to 1.8 mm/s.', 'engineer', now() - interval '10 days 20 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000004', 'CREATED', 'Work order created', 'engineer', now() - interval '3 days'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000004', 'STATUS_CHANGED', 'OPEN → IN_PROGRESS', 'engineer', now() - interval '2 days 4 hours'),
    (gen_random_uuid(), '55555555-0000-0000-0000-000000000004', 'STATUS_CHANGED', 'IN_PROGRESS → ON_HOLD: Waiting for the FANUC calibration jig.', 'engineer', now() - interval '2 days');
