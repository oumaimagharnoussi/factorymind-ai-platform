-- Demo data only: a small plant store. Prices are illustrative.
-- The three ISSUE movements match the parts booked on maintenance-service's demo work orders.

INSERT INTO spare_parts (id, part_number, name, description, category, unit, quantity_on_hand, minimum_stock, unit_cost, location, created_at, updated_at) VALUES
    ('44444444-0000-0000-0000-000000000001', 'SKF-6309-2Z', 'Deep groove ball bearing 6309-2Z', 'Conveyor motor, both ends', 'MECHANICAL', 'pcs', 6, 4, 36.90, 'A-01-03', now(), now()),
    ('44444444-0000-0000-0000-000000000002', 'SKF-6205-2RS', 'Deep groove ball bearing 6205-2RS', 'Coolant pump and small motors', 'MECHANICAL', 'pcs', 14, 6, 8.40, 'A-01-04', now(), now()),
    ('44444444-0000-0000-0000-000000000003', 'GATES-SPB-1600', 'V-belt SPB 1600', 'Packaging line drive', 'MECHANICAL', 'pcs', 3, 4, 19.80, 'A-02-01', now(), now()),
    ('44444444-0000-0000-0000-000000000004', 'AC-2914-9306', 'Compressor air filter element', 'Atlas Copco GA 37', 'FILTER', 'pcs', 1, 2, 48.50, 'B-01-02', now(), now()),
    ('44444444-0000-0000-0000-000000000005', 'AC-ROTOR-XTEND', 'Compressor oil Roto-Xtend', 'Atlas Copco GA 37, 4,000-hour service', 'LUBRICANT', 'L', 15, 20, 22.00, 'C-01-01', now(), now()),
    ('44444444-0000-0000-0000-000000000006', 'HLP-46', 'Hydraulic oil HLP 46', 'Hydraulic press HP-400', 'LUBRICANT', 'L', 120, 60, 4.80, 'C-01-02', now(), now()),
    ('44444444-0000-0000-0000-000000000007', 'SCH-SEAL-HP400', 'Hydraulic press seal kit HP-400', 'Main cylinder seals', 'HYDRAULIC', 'set', 2, 1, 310.00, 'B-02-01', now(), now()),
    ('44444444-0000-0000-0000-000000000008', 'ABB-AF26-30-00', 'Contactor AF26-30-00', 'Motor starters, 24 V coil', 'ELECTRICAL', 'pcs', 5, 2, 64.00, 'D-01-01', now(), now()),
    ('44444444-0000-0000-0000-000000000009', 'PT100-M12', 'PT100 temperature probe M12', 'Replacement for machine temperature sensors', 'SENSOR', 'pcs', 4, 2, 39.00, 'D-02-01', now(), now()),
    ('44444444-0000-0000-0000-000000000010', 'ACC-IEPE-100', 'Accelerometer 100 mV/g', 'Replacement for machine vibration sensors', 'SENSOR', 'pcs', 0, 1, 185.00, 'D-02-02', now(), now()),
    ('44444444-0000-0000-0000-000000000011', 'DMG-SPINDLE-BRG', 'Spindle bearing set DMU 50', 'CNC milling center spindle', 'MECHANICAL', 'set', 1, 1, 1450.00, 'A-03-01', now(), now()),
    ('44444444-0000-0000-0000-000000000012', 'GRF-CR10-SEAL', 'Mechanical shaft seal CR 10', 'Grundfos coolant pump', 'HYDRAULIC', 'pcs', 2, 1, 96.00, 'B-02-02', now(), now());

-- Opening stock: one delivery per part (what is on hand now plus what the demo work orders used).
INSERT INTO stock_movements (id, part_id, type, quantity_change, quantity_after, unit_cost, reference, note, performed_by, performed_at)
SELECT gen_random_uuid(), p.id, 'RECEIPT', p.quantity_on_hand + COALESCE(u.used, 0), p.quantity_on_hand + COALESCE(u.used, 0),
       p.unit_cost, 'OPENING-STOCK', 'Initial stock', 'admin', now() - interval '30 days'
FROM spare_parts p
LEFT JOIN (VALUES
    ('44444444-0000-0000-0000-000000000001'::uuid, 2),
    ('44444444-0000-0000-0000-000000000004'::uuid, 1),
    ('44444444-0000-0000-0000-000000000005'::uuid, 5)
) AS u (part_id, used) ON u.part_id = p.id
WHERE p.quantity_on_hand + COALESCE(u.used, 0) > 0;

INSERT INTO stock_movements (id, part_id, type, quantity_change, quantity_after, unit_cost, request_id, work_order_id, work_order_code, performed_by, performed_at) VALUES
    (gen_random_uuid(), '44444444-0000-0000-0000-000000000001', 'ISSUE', -2, 6, 36.90, '66666666-0000-0000-0000-000000000003',
     '55555555-0000-0000-0000-000000000003', 'WO-00003', 'engineer', now() - interval '10 days 22 hours'),
    (gen_random_uuid(), '44444444-0000-0000-0000-000000000004', 'ISSUE', -1, 1, 48.50, '66666666-0000-0000-0000-000000000001',
     '55555555-0000-0000-0000-000000000001', 'WO-00001', 'engineer', now() - interval '90 minutes'),
    (gen_random_uuid(), '44444444-0000-0000-0000-000000000005', 'ISSUE', -5, 15, 22.00, '66666666-0000-0000-0000-000000000002',
     '55555555-0000-0000-0000-000000000001', 'WO-00001', 'engineer', now() - interval '80 minutes');
