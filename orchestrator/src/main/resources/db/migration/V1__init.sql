-- V1__init.sql (orchestrator)
-- Baseline: project_state, decision_log (создано Hibernate ddl-auto: update, зафиксировано Flyway)

CREATE TABLE IF NOT EXISTS project_state (
    id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    requested_by VARCHAR(128),
    state VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version VARCHAR(64),
    payload JSONB,
    CONSTRAINT project_state_pkey PRIMARY KEY (id),
    CONSTRAINT idx_ps_project UNIQUE (project_id),
    CONSTRAINT project_state_state_check CHECK (state IN (
        'PROJECT_INIT', 'GEOMETRY_INPUT', 'GEOMETRY_ANALYSIS',
        'ASSEMBLY_ANALYSIS', 'PART_PROCESS_PLANNING', 'TOOLING_DESIGN',
        'EQUIPMENT_MATCHING', 'EQUIPMENT_GAP_RESOLUTION', 'RECOMMENDATION_REVIEW',
        'ASSEMBLY_PROCESS_PLANNING', 'NORMING', 'DOCUMENTATION',
        'TECHNICAL_CONTROL', 'COMPLETED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_ps_state ON project_state(state);

CREATE TABLE IF NOT EXISTS decision_log (
    id VARCHAR(64) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT decision_log_pkey PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_dl_created ON decision_log(created_at);
CREATE INDEX IF NOT EXISTS idx_dl_project ON decision_log(project_id);