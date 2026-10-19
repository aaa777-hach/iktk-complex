-- V2__seed.sql — тестовая геометрия
--   geo-12345 — «сталь», простая деталь
--   geo-test  — «алюминий», сложная деталь (undercuts=true)

INSERT INTO geometry (id, object_ref, project_id, material,
                      volume, mass, min_wall_thickness, min_fillet_radius,
                      min_draft_angle, nominal_dimensions, complexity_score,
                      undercuts, hot_spots, created_at, updated_at)
VALUES
    ('geo-id-12345', 'geo-12345', NULL, 'Steel 20Kh',
     1000.0000, 7.8500, 4.0000, 2.0000, 1.0000, 100.0000, 2,
     FALSE, FALSE, NOW(), NOW()),

    ('geo-id-test', 'geo-test', NULL, 'AlSi10Mg',
     2500.0000, 6.7500, 3.0000, 1.5000, 0.5000, 250.0000, 4,
     TRUE, FALSE, NOW(), NOW())
ON CONFLICT (object_ref) DO NOTHING;