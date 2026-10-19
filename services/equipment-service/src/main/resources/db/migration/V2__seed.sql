-- V2__seed.sql — тестовые данные: 1 единица оборудования + 5 способов литья
-- equipment_type = CASTING_MACHINE (общий тип), operation_type = способ литья

INSERT INTO equipment (id, name, equipment_type, model, manufacturer, inventory_no,
                       enterprise_code, department_code, location, status,
                       commissioned_at, parameters, created_at, updated_at)
VALUES (
    'eq-casting-01',
    'Литейная машина (тестовая)',
    'CASTING_MACHINE',
    'LK-Machinery',
    'Test Manufacturer',
    'INV-CAST-01',
    'PLANT-01',
    'FOUNDRY-01',
    'Цех литья, участок 1',
    'AVAILABLE',
    NOW(),
    '{"source":"seed"}'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO equipment_capabilities (id, equipment_id, operation_type, operation_name,
                                    operation_category, cnc_capable, parameters,
                                    created_at, updated_at)
VALUES
    ('cap-sand-01',  'eq-casting-01', 'SAND_CASTING',        'Литьё в песчаные формы',       'CASTING', FALSE, '{"source":"seed"}'::jsonb, NOW(), NOW()),
    ('cap-inv-01',   'eq-casting-01', 'INVESTMENT_CASTING',  'Литьё по выплавляемым моделям','CASTING', FALSE, '{"source":"seed"}'::jsonb, NOW(), NOW()),
    ('cap-die-01',   'eq-casting-01', 'DIE_CASTING',         'Литьё под давлением',          'CASTING', TRUE,  '{"source":"seed"}'::jsonb, NOW(), NOW()),
    ('cap-grav-01',  'eq-casting-01', 'GRAVITY_CASTING',     'Гравитационное литьё',         'CASTING', FALSE, '{"source":"seed"}'::jsonb, NOW(), NOW()),
    ('cap-centr-01', 'eq-casting-01', 'CENTRIFUGAL_CASTING', 'Центробежное литьё',           'CASTING', FALSE, '{"source":"seed"}'::jsonb, NOW(), NOW())
ON CONFLICT (equipment_id, operation_type) DO NOTHING;

INSERT INTO equipment_capability_materials (capability_id, material_code)
VALUES
    ('cap-sand-01',  'Steel 20Kh'),
    ('cap-inv-01',   'Steel 20Kh'),
    ('cap-die-01',   'AlSi10Mg'),
    ('cap-grav-01',  'Steel 20Kh'),
    ('cap-centr-01', 'Steel 20Kh')
ON CONFLICT DO NOTHING;