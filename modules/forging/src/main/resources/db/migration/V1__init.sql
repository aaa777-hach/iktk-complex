-- V1__init.sql (forging)

CREATE TABLE IF NOT EXISTS forging_process (
    id                        VARCHAR(64) PRIMARY KEY,
    project_id                VARCHAR(64) NOT NULL,
    object_ref                VARCHAR(255),
    method                    VARCHAR(32),
    billet                    JSONB,
    transitions               JSONB,
    forces                    JSONB,
    tooling                   JSONB,
    manufacturability_report  JSONB,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fp_project ON forging_process (project_id);
CREATE INDEX IF NOT EXISTS idx_fp_method ON forging_process (method);
