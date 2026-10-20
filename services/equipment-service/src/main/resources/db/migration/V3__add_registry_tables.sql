-- V3__add_registry_tables.sql (iktk_equipment)
-- Закрывает 4 из 7 таблиц реестра, отложенных в V1:
--   equipment_status      — текущее состояние/загрузка (1:1 с equipment)
--   equipment_tooling     — совместимость с оснасткой (M:N)
--   equipment_requirements — требования из ТП
--   equipment_gap_analysis — результаты gap-анализа
-- Внешние сущности (projects, tooling, technology_processes) в module-БД отсутствуют,
-- поэтому *_id — VARCHAR(64) без FK. FK только на equipment.

-- -----------------------------------------------------------------------------
-- 1. Текущее состояние и загрузка оборудования
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_status (
    id                  VARCHAR(64) PRIMARY KEY,
    equipment_id        VARCHAR(64) NOT NULL UNIQUE,
    state               VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    load_percent        INT NOT NULL DEFAULT 0 CHECK (load_percent BETWEEN 0 AND 100),
    schedule            VARCHAR(64),
    last_used_at        TIMESTAMPTZ,
    next_maintenance_at TIMESTAMPTZ,
    notes               VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_status_equipment FOREIGN KEY (equipment_id)
        REFERENCES equipment(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_status_equipment  ON equipment_status(equipment_id);
CREATE INDEX IF NOT EXISTS idx_status_state      ON equipment_status(state);
CREATE INDEX IF NOT EXISTS idx_status_load       ON equipment_status(load_percent DESC);
CREATE INDEX IF NOT EXISTS idx_status_next_maint ON equipment_status(next_maintenance_at);

-- -----------------------------------------------------------------------------
-- 2. Совместимость оборудования и оснастки
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_tooling (
    equipment_id    VARCHAR(64) NOT NULL,
    tooling_id      VARCHAR(64) NOT NULL,
    compatibility   VARCHAR(32) NOT NULL DEFAULT 'FULL',
    notes           VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (equipment_id, tooling_id),
    CONSTRAINT fk_etooling_equipment FOREIGN KEY (equipment_id)
        REFERENCES equipment(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_etooling_tooling ON equipment_tooling(tooling_id);

-- -----------------------------------------------------------------------------
-- 3. Требования к оборудованию (из операций ТП)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_requirements (
    id                       VARCHAR(64) PRIMARY KEY,
    project_id               VARCHAR(64) NOT NULL,
    process_id               VARCHAR(64),
    stage_id                 VARCHAR(64),
    operation_id             VARCHAR(64),
    analysis_id              VARCHAR(64),
    operation_type           VARCHAR(64) NOT NULL,
    required_equipment_type  VARCHAR(64),
    required_accuracy        VARCHAR(32),
    max_dimensions           VARCHAR(64),
    min_dimensions           VARCHAR(64),
    material                 VARCHAR(128),
    required_power_kw        NUMERIC(10,3),
    required_force_kn        NUMERIC(12,3),
    required_cnc             BOOLEAN NOT NULL DEFAULT FALSE,
    required_axes            INT,
    required_temperature_c   NUMERIC(8,2),
    quantity                 INT NOT NULL DEFAULT 1,
    priority                 INT NOT NULL DEFAULT 0 CHECK (priority BETWEEN 0 AND 10),
    notes                    VARCHAR(1000),
    parameters               JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_req_project   ON equipment_requirements(project_id);
CREATE INDEX IF NOT EXISTS idx_req_process   ON equipment_requirements(process_id);
CREATE INDEX IF NOT EXISTS idx_req_operation ON equipment_requirements(operation_type);
CREATE INDEX IF NOT EXISTS idx_req_priority  ON equipment_requirements(priority DESC);

-- -----------------------------------------------------------------------------
-- 4. Результаты gap-анализа
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment_gap_analysis (
    id                    VARCHAR(64) PRIMARY KEY,
    project_id            VARCHAR(64) NOT NULL,
    strategy              VARCHAR(32) NOT NULL DEFAULT 'STRICT',
    status                VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    requested_by          VARCHAR(128),
    gaps_count            INT NOT NULL DEFAULT 0,
    recommendations_count INT NOT NULL DEFAULT 0,
    gaps                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary               JSONB,
    started_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ,
    error                 VARCHAR(2000),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gap_project    ON equipment_gap_analysis(project_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_gap_strategy   ON equipment_gap_analysis(strategy);
CREATE INDEX IF NOT EXISTS idx_gap_status     ON equipment_gap_analysis(status);
CREATE INDEX IF NOT EXISTS idx_gap_started_at ON equipment_gap_analysis(started_at DESC);