-- Demo data only. Machine ids come from asset-service's demo data. Dates are relative to today so the demo always
-- shows one plan due now (M-101: the planner opens its work order a minute after start), one waiting on an open
-- job (M-103), some scheduled later and one paused.

INSERT INTO maintenance_plans (id, code, asset_id, title, instructions, schedule, interval_days, lead_days, priority,
                               assignee, active, next_due_date, last_generated_at, created_by, created_at, updated_at) VALUES
    ('77777777-0000-0000-0000-000000000001', 'PM-00001', '33333333-0000-0000-0000-000000000103',
     '4,000-hour service: air filter and oil',
     'Replace the air filter element and the Roto-Xtend oil. Check the oil separator pressure drop and the belt tension.',
     'FLOATING', 90, 7, 'MEDIUM', 'engineer', TRUE, current_date, now() - interval '1 day', 'engineer',
     now() - interval '200 days', now() - interval '1 day'),
    ('77777777-0000-0000-0000-000000000002', 'PM-00002', '33333333-0000-0000-0000-000000000101',
     'Lubricate conveyor motor bearings',
     'Grease both bearings (8 g each, Mobil Polyrex EM). Listen for noise and record the vibration at the drive end.',
     'FIXED', 30, 3, 'MEDIUM', 'engineer', TRUE, current_date + 2, NULL, 'engineer',
     now() - interval '120 days', now() - interval '120 days'),
    ('77777777-0000-0000-0000-000000000003', 'PM-00003', '33333333-0000-0000-0000-000000000102',
     'Hydraulic oil and filter check',
     'Check the oil level and colour, the return filter clogging indicator and the hoses for leaks.',
     'FIXED', 14, 2, 'HIGH', NULL, TRUE, current_date + 10, now() - interval '6 days', 'engineer',
     now() - interval '90 days', now() - interval '6 days'),
    ('77777777-0000-0000-0000-000000000004', 'PM-00004', '33333333-0000-0000-0000-000000000107',
     'Coolant pump seal and impeller inspection',
     'Stop the pump, inspect the mechanical seal for leaks and the impeller for wear. Replace the seal if it weeps.',
     'FLOATING', 180, 7, 'LOW', NULL, TRUE, current_date + 45, NULL, 'admin',
     now() - interval '135 days', now() - interval '135 days'),
    ('77777777-0000-0000-0000-000000000005', 'PM-00005', '33333333-0000-0000-0000-000000000105',
     'Grease robot axes and check cables',
     'Grease J1 to J3 reducers and check the dress pack cables for wear at the wrist.',
     'FIXED', 60, 5, 'MEDIUM', NULL, FALSE, current_date + 20, NULL, 'admin',
     now() - interval '40 days', now() - interval '10 days');

SELECT setval('maintenance_plan_number_seq', 5);

-- The compressor service in progress (WO-00001) is the current job of its plan.
UPDATE work_orders SET plan_id = '77777777-0000-0000-0000-000000000001'
WHERE id = '55555555-0000-0000-0000-000000000001';
