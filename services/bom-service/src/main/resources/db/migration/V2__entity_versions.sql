-- V2__entity_versions.sql (bom-service)
-- Схема iktk + таблица entity_versions для VersioningService (shared).
-- VersioningService (shared) работает с iktk.entity_versions, но в bom-service
-- эта таблица не создавалась (была только в orchestrator, но и там — нет миграции).

CREATE SCHEMA IF NOT EXISTS iktk;

CREATE TABLE IF NOT EXISTS iktk.entity_versions (
    id              UUID PRIMARY KEY,
    entity_type     VARCHAR(64)  NOT NULL,
    entity_id       UUID         NOT NULL,
    project_id      UUID         NOT NULL,
    version         VARCHAR(16)  NOT NULL,
    version_number  INTEGER      NOT NULL,
    state           VARCHAR(64),
    snapshot        JSONB,
    diff            JSONB,
    change_type     VARCHAR(32)  NOT NULL,
    change_reason   VARCHAR(1000),
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    is_current      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_ev_entity
    ON iktk.entity_versions (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_ev_project
    ON iktk.entity_versions (project_id);
CREATE INDEX IF NOT EXISTS idx_ev_current
    ON iktk.entity_versions (entity_type, entity_id, is_current);
