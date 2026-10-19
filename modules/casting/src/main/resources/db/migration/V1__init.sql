-- V1__init.sql (casting)
-- Baseline: casting_process (создано Hibernate ddl-auto: update, зафиксировано Flyway)

CREATE TABLE IF NOT EXISTS casting_process (
    id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    gate_area_mm2 NUMERIC(12,4),
    gates_count INTEGER,
    metal_volume_mm3 NUMERIC(18,4),
    pouring_time_sec NUMERIC(10,4),
    risers_count INTEGER,
    runner_area_mm2 NUMERIC(12,4),
    sprue_diameter_mm NUMERIC(10,4),
    method VARCHAR(50),
    object_ref VARCHAR(255),
    project_id VARCHAR(64) NOT NULL,
    linear_allowance_mm NUMERIC(8,4),
    shrinkage_material VARCHAR(100),
    shrinkage_method VARCHAR(50),
    shrinkage_percent NUMERIC(8,4),
    CONSTRAINT casting_process_pkey PRIMARY KEY (id),
    CONSTRAINT casting_process_method_check CHECK (method IN (
        'SAND_CASTING', 'DIE_CASTING', 'INVESTMENT_CASTING',
        'CENTRIFUGAL_CASTING', 'GRAVITY_CASTING'
    )),
    CONSTRAINT casting_process_shrinkage_method_check CHECK (shrinkage_method IN (
        'SAND_CASTING', 'DIE_CASTING', 'INVESTMENT_CASTING',
        'CENTRIFUGAL_CASTING', 'GRAVITY_CASTING'
    ))
);