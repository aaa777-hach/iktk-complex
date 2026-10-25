-- ИКТК: модуль нормирования, начальная схема
-- Таблицы: time_norms, material_norms (JSONB для вложенных списков)

CREATE TABLE IF NOT EXISTS time_norms (
    id              VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(64) NOT NULL,
    process_id      VARCHAR(64),
    source          VARCHAR(32),
    batch_size      NUMERIC(12,4),
    stage_norms     JSONB,
    total_tpz       NUMERIC(12,4),
    total_tsh       NUMERIC(12,4),
    total_tsh_t     NUMERIC(12,4),
    total_tsh_k     NUMERIC(12,4),
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tn_project ON time_norms (project_id);
CREATE INDEX IF NOT EXISTS idx_tn_process ON time_norms (process_id);
CREATE INDEX IF NOT EXISTS idx_tn_created ON time_norms (created_at);

CREATE TABLE IF NOT EXISTS material_norms (
    id                     VARCHAR(64) PRIMARY KEY,
    project_id             VARCHAR(64) NOT NULL,
    process_id             VARCHAR(64),
    material               JSONB,
    blank_mass             NUMERIC(12,4),
    part_mass              NUMERIC(12,4),
    consumptions           JSONB,
    tool_consumptions      JSONB,
    norm_per_part          NUMERIC(12,4),
    norm_per_batch         NUMERIC(12,4),
    batch_size             NUMERIC(12,4),
    material_utilization   NUMERIC(12,4),
    tech_loss_coefficient  NUMERIC(12,4),
    defect_coefficient     NUMERIC(12,4),
    created_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mn_project ON material_norms (project_id);
CREATE INDEX IF NOT EXISTS idx_mn_process ON material_norms (process_id);
CREATE INDEX IF NOT EXISTS idx_mn_created ON material_norms (created_at);
