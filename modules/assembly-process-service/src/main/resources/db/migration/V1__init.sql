-- V1__init.sql (assembly-process-service)
-- Схема для проектирования ТП сборки ИКТК.

CREATE TABLE IF NOT EXISTS assembly_process (
    id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    project_id VARCHAR(64) NOT NULL,
    object_ref VARCHAR(255),
    strategy VARCHAR(50),
    stage_count INTEGER,
    component_count INTEGER,
    total_time_min NUMERIC(12,4),
    status VARCHAR(32),
    notes VARCHAR(2000),
    CONSTRAINT assembly_process_pkey PRIMARY KEY (id),
    CONSTRAINT assembly_process_strategy_check CHECK (strategy IN (
        'SEQUENTIAL', 'PARALLEL', 'MIXED'
    )),
    CONSTRAINT assembly_process_status_check CHECK (status IN (
        'DRAFT', 'APPROVED', 'REJECTED', 'ARCHIVED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_assembly_process_project ON assembly_process(project_id);
CREATE INDEX IF NOT EXISTS idx_assembly_process_status ON assembly_process(status);

CREATE TABLE IF NOT EXISTS assembly_stage (
    id VARCHAR(64) NOT NULL,
    assembly_process_id VARCHAR(64) NOT NULL,
    stage_order INTEGER NOT NULL,
    designation VARCHAR(64),
    name VARCHAR(255),
    operation_type VARCHAR(64),
    equipment_id VARCHAR(64),
    tooling_id VARCHAR(64),
    time_norm_min NUMERIC(12,4),
    setup_time_min NUMERIC(12,4),
    comment VARCHAR(1000),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assembly_stage_pkey PRIMARY KEY (id),
    CONSTRAINT fk_assembly_stage_process FOREIGN KEY (assembly_process_id)
        REFERENCES assembly_process(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assembly_stage_process ON assembly_stage(assembly_process_id);
CREATE INDEX IF NOT EXISTS idx_assembly_stage_order ON assembly_stage(assembly_process_id, stage_order);

CREATE TABLE IF NOT EXISTS assembly_component (
    id VARCHAR(64) NOT NULL,
    assembly_process_id VARCHAR(64) NOT NULL,
    parent_component_id VARCHAR(64),
    designation VARCHAR(128),
    name VARCHAR(255),
    quantity INTEGER NOT NULL DEFAULT 1,
    position INTEGER,
    part_kind VARCHAR(32),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assembly_component_pkey PRIMARY KEY (id),
    CONSTRAINT fk_assembly_component_process FOREIGN KEY (assembly_process_id)
        REFERENCES assembly_process(id) ON DELETE CASCADE,
    CONSTRAINT fk_assembly_component_parent FOREIGN KEY (parent_component_id)
        REFERENCES assembly_component(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assembly_component_process ON assembly_component(assembly_process_id);
CREATE INDEX IF NOT EXISTS idx_assembly_component_parent ON assembly_component(parent_component_id);

CREATE TABLE IF NOT EXISTS assembly_tooling (
    id VARCHAR(64) NOT NULL,
    assembly_process_id VARCHAR(64) NOT NULL,
    tooling_id VARCHAR(64) NOT NULL,
    compatibility VARCHAR(32),
    notes VARCHAR(512),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT assembly_tooling_pkey PRIMARY KEY (id),
    CONSTRAINT fk_assembly_tooling_process FOREIGN KEY (assembly_process_id)
        REFERENCES assembly_process(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assembly_tooling_process ON assembly_tooling(assembly_process_id);
CREATE INDEX IF NOT EXISTS idx_assembly_tooling_tooling ON assembly_tooling(tooling_id);