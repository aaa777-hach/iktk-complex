-- =============================================================================
-- ИКТК: версионность, аудит изменений и откат
-- Миграция: V3__versioning.sql
-- Зависит от: V1__init_schema.sql, V2__project_tables.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Универсальный журнал версий сущностей
-- -----------------------------------------------------------------------------

-- Централизованная версионность: одна таблица для всех версионируемых
-- сущностей ИКТК. Позволяет хранить историю изменений проектов, ТП,
-- этапов, оснастки, BOM и др. с возможностью отката.

CREATE TABLE IF NOT EXISTS iktk.entity_versions (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    entity_type       VARCHAR(64) NOT NULL,
    entity_id         UUID NOT NULL,
    project_id        UUID REFERENCES iktk.projects(id) ON DELETE CASCADE,
    version           VARCHAR(64) NOT NULL,
    version_number    INT NOT NULL,
    state             VARCHAR(64),
    snapshot          JSONB NOT NULL,
    diff              JSONB,
    change_type       VARCHAR(32) NOT NULL,
    change_reason     VARCHAR(512),
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_current        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (entity_type, entity_id, version)
);

COMMENT ON TABLE  iktk.entity_versions IS 'Универсальный журнал версий сущностей';
COMMENT ON COLUMN iktk.entity_versions.entity_type IS 'Тип сущности: PROJECT, PROCESS, STAGE, TOOLING, BOM, ...';
COMMENT ON COLUMN iktk.entity_versions.entity_id IS 'Идентификатор версионируемой сущности';
COMMENT ON COLUMN iktk.entity_versions.version IS 'Метка версии (v1, v2, ...)';
COMMENT ON COLUMN iktk.entity_versions.version_number IS 'Порядковый номер версии';
COMMENT ON COLUMN iktk.entity_versions.snapshot IS 'Полный снапшот сущности (JSONB)';
COMMENT ON COLUMN iktk.entity_versions.diff IS 'Разница с предыдущей версией (JSONB)';
COMMENT ON COLUMN iktk.entity_versions.change_type IS 'Тип изменения: CREATE, UPDATE, DELETE, ROLLBACK, APPROVE, REJECT';
COMMENT ON COLUMN iktk.entity_versions.is_current IS 'Признак актуальной версии';

CREATE INDEX IF NOT EXISTS idx_entity_versions_entity
    ON iktk.entity_versions(entity_type, entity_id, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_entity_versions_project
    ON iktk.entity_versions(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_entity_versions_current
    ON iktk.entity_versions(entity_type, entity_id) WHERE is_current = TRUE;
CREATE INDEX IF NOT EXISTS idx_entity_versions_created
    ON iktk.entity_versions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_entity_versions_change_type
    ON iktk.entity_versions(change_type);

-- -----------------------------------------------------------------------------
-- 2. Точки отката (checkpoint)
-- -----------------------------------------------------------------------------

-- Именованные точки отката: фиксируют состояние проекта или ТП
-- на ключевых этапах, чтобы можно было вернуться к ним одной командой.

CREATE TABLE IF NOT EXISTS iktk.rollback_points (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    entity_type       VARCHAR(64),
    entity_id         UUID,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    state             VARCHAR(64),
    snapshot_ref      UUID REFERENCES iktk.entity_versions(id) ON DELETE SET NULL,
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_automatic      BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE  iktk.rollback_points IS 'Именованные точки отката проекта';
COMMENT ON COLUMN iktk.rollback_points.snapshot_ref IS 'Ссылка на снапшот в entity_versions';
COMMENT ON COLUMN iktk.rollback_points.is_automatic IS 'Автоматически созданная точка (на ключевых этапах)';

CREATE INDEX IF NOT EXISTS idx_rollback_project
    ON iktk.rollback_points(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rollback_entity
    ON iktk.rollback_points(entity_type, entity_id);

-- -----------------------------------------------------------------------------
-- 3. Журнал операций отката
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.rollback_history (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    rollback_point_id UUID REFERENCES iktk.rollback_points(id) ON DELETE SET NULL,
    from_version      VARCHAR(64),
    to_version        VARCHAR(64) NOT NULL,
    from_state        VARCHAR(64),
    to_state          VARCHAR(64) NOT NULL,
    reason            VARCHAR(1000),
    performed_by      VARCHAR(128),
    performed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    success           BOOLEAN NOT NULL DEFAULT TRUE,
    details           JSONB
);

COMMENT ON TABLE  iktk.rollback_history IS 'История операций отката проекта';

CREATE INDEX IF NOT EXISTS idx_rollback_history_project
    ON iktk.rollback_history(project_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_rollback_history_success
    ON iktk.rollback_history(success) WHERE success = FALSE;

-- -----------------------------------------------------------------------------
-- 4. Аудит изменений (детальный, на уровне полей)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk_audit.change_log (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    entity_type       VARCHAR(64) NOT NULL,
    entity_id         UUID NOT NULL,
    project_id        UUID REFERENCES iktk.projects(id) ON DELETE SET NULL,
    operation         VARCHAR(32) NOT NULL,
    field_name        VARCHAR(128),
    old_value         TEXT,
    new_value         TEXT,
    changed_by        VARCHAR(128),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_id        VARCHAR(64),
    correlation_id    VARCHAR(64),
    transaction_id    VARCHAR(64)
);

COMMENT ON TABLE  iktk_audit.change_log IS 'Детальный аудит изменений на уровне полей';
COMMENT ON COLUMN iktk_audit.change_log.operation IS 'INSERT, UPDATE, DELETE';
COMMENT ON COLUMN iktk_audit.change_log.field_name IS 'Имя изменённого поля';

CREATE INDEX IF NOT EXISTS idx_change_log_entity
    ON iktk_audit.change_log(entity_type, entity_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_log_project
    ON iktk_audit.change_log(project_id, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_log_user
    ON iktk_audit.change_log(changed_by, changed_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_log_tx
    ON iktk_audit.change_log(transaction_id);
CREATE INDEX IF NOT EXISTS idx_change_log_request
    ON iktk_audit.change_log(request_id);

-- -----------------------------------------------------------------------------
-- 5. Блокировки (оптимистичные и пессимистичные)
-- -----------------------------------------------------------------------------

-- Оптимистичные блокировки: версия сущности, при несовпадении —
-- конфликт. Используется в сервисах через поле version.

CREATE TABLE IF NOT EXISTS iktk.entity_locks (
    entity_type       VARCHAR(64) NOT NULL,
    entity_id         UUID NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,
    locked_by         VARCHAR(128),
    locked_at         TIMESTAMPTZ,
    lock_type         VARCHAR(32) NOT NULL DEFAULT 'OPTIMISTIC',
    lock_expires_at   TIMESTAMPTZ,
    PRIMARY KEY (entity_type, entity_id)
);

COMMENT ON TABLE  iktk.entity_locks IS 'Блокировки сущностей (оптимистичные/пессимистичные)';
COMMENT ON COLUMN iktk.entity_locks.version IS 'Счётчик версии (для оптимистичной блокировки)';
COMMENT ON COLUMN iktk.entity_locks.lock_type IS 'OPTIMISTIC или PESSIMISTIC';
COMMENT ON COLUMN iktk.entity_locks.lock_expires_at IS 'Истечение пессимистичной блокировки';

CREATE INDEX IF NOT EXISTS idx_entity_locks_expires
    ON iktk.entity_locks(lock_expires_at) WHERE lock_expires_at IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 6. Триггеры версионности
-- -----------------------------------------------------------------------------

-- 6.1. Функция создания версии при изменении проекта
CREATE OR REPLACE FUNCTION iktk.on_project_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'PROJECT'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        -- Снимаем флаг is_current с предыдущих версий
        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'PROJECT'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        -- Записываем новую версию
        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, created_by, is_current
        ) VALUES (
            'PROJECT', NEW.id, NEW.id, v_version_label, v_version_number,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'UPDATE',
            NEW.updated_at::TEXT,
            TRUE
        );

    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'PROJECT', NEW.id, NEW.id, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_project_change() IS 'Триггер версионности проектов';

CREATE TRIGGER trg_project_versioning
    AFTER INSERT OR UPDATE ON iktk.projects
    FOR EACH ROW EXECUTE FUNCTION iktk.on_project_change();

-- 6.2. Функция создания версии при изменении ТП
CREATE OR REPLACE FUNCTION iktk.on_process_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'PROCESS'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'PROCESS'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'PROCESS', NEW.id, NEW.project_id, v_version_label, v_version_number,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'UPDATE',
            TRUE
        );

    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'PROCESS', NEW.id, NEW.project_id, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_process_change() IS 'Триггер версионности ТП';

CREATE TRIGGER trg_process_versioning
    AFTER INSERT OR UPDATE ON iktk.technology_processes
    FOR EACH ROW EXECUTE FUNCTION iktk.on_process_change();

-- 6.3. Функция создания версии при изменении этапа
CREATE OR REPLACE FUNCTION iktk.on_stage_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
    v_project_id     UUID;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT project_id INTO v_project_id
          FROM iktk.technology_processes
         WHERE id = NEW.technology_process_id;

        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'STAGE'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'STAGE'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'STAGE', NEW.id, v_project_id, v_version_label, v_version_number,
            NEW.stage_kind::TEXT,
            to_jsonb(NEW),
            'UPDATE',
            TRUE
        );

    ELSIF TG_OP = 'INSERT' THEN
        SELECT project_id INTO v_project_id
          FROM iktk.technology_processes
         WHERE id = NEW.technology_process_id;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'STAGE', NEW.id, v_project_id, 'v1', 1,
            NEW.stage_kind::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_stage_change() IS 'Триггер версионности этапов ТП';

CREATE TRIGGER trg_stage_versioning
    AFTER INSERT OR UPDATE ON iktk.stages
    FOR EACH ROW EXECUTE FUNCTION iktk.on_stage_change();

-- -----------------------------------------------------------------------------
-- 7. Функции отката
-- -----------------------------------------------------------------------------

-- 7.1. Функция отката проекта к указанной версии
CREATE OR REPLACE FUNCTION iktk.rollback_project(
    p_project_id UUID,
    p_version    VARCHAR(64),
    p_reason     VARCHAR(1000),
    p_performed_by VARCHAR(128)
)
RETURNS TABLE (
    success         BOOLEAN,
    from_version    VARCHAR(64),
    to_version      VARCHAR(64),
    message         TEXT
) AS $$
DECLARE
    v_snapshot      JSONB;
    v_from_version  VARCHAR(64);
    v_from_state    VARCHAR(64);
    v_to_state      VARCHAR(64);
BEGIN
    -- Текущая версия
    SELECT version, snapshot->>'status'
      INTO v_from_version, v_from_state
      FROM iktk.entity_versions
     WHERE entity_type = 'PROJECT'
       AND entity_id   = p_project_id
       AND is_current  = TRUE;

    -- Целевая версия
    SELECT snapshot, snapshot->>'status'
      INTO v_snapshot, v_to_state
      FROM iktk.entity_versions
     WHERE entity_type = 'PROJECT'
       AND entity_id   = p_project_id
       AND version     = p_version;

    IF v_snapshot IS NULL THEN
        RETURN QUERY SELECT FALSE, v_from_version, p_version,
            'Версия не найдена: ' || p_version;
        RETURN;
    END IF;

    -- Применяем снапшот
    UPDATE iktk.projects
       SET designation          = v_snapshot->>'designation',
           name                 = v_snapshot->>'name',
           description          = v_snapshot->>'description',
           status               = (v_snapshot->>'status')::iktk.project_status,
           responsible_engineer = v_snapshot->>'responsible_engineer'
     WHERE id = p_project_id;

    -- Сбрасываем is_current у всех версий, ставим на целевую
    UPDATE iktk.entity_versions
       SET is_current = FALSE
     WHERE entity_type = 'PROJECT'
       AND entity_id   = p_project_id;

    UPDATE iktk.entity_versions
       SET is_current = TRUE
     WHERE entity_type = 'PROJECT'
       AND entity_id   = p_project_id
       AND version     = p_version;

    -- Записываем операцию отката
    INSERT INTO iktk.rollback_history (
        project_id, from_version, to_version,
        from_state, to_state, reason, performed_by, success, details
    ) VALUES (
        p_project_id, v_from_version, p_version,
        v_from_state, v_to_state, p_reason, p_performed_by, TRUE,
        jsonb_build_object('snapshot_applied', TRUE)
    );

    RETURN QUERY SELECT TRUE, v_from_version, p_version,
        'Откат выполнен успешно';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.rollback_project IS 'Откат проекта к указанной версии';

-- 7.2. Функция создания точки отката
CREATE OR REPLACE FUNCTION iktk.create_rollback_point(
    p_project_id UUID,
    p_name       VARCHAR(255),
    p_description VARCHAR(1000),
    p_created_by VARCHAR(128),
    p_is_automatic BOOLEAN DEFAULT FALSE
)
RETURNS UUID AS $$
DECLARE
    v_point_id      UUID;
    v_snapshot_ref  UUID;
    v_state         VARCHAR(64);
BEGIN
    -- Текущая актуальная версия
    SELECT id, state
      INTO v_snapshot_ref, v_state
      FROM iktk.entity_versions
     WHERE entity_type = 'PROJECT'
       AND entity_id   = p_project_id
       AND is_current  = TRUE
     LIMIT 1;

    INSERT INTO iktk.rollback_points (
        project_id, entity_type, entity_id, name, description,
        state, snapshot_ref, created_by, is_automatic
    ) VALUES (
        p_project_id, 'PROJECT', p_project_id, p_name, p_description,
        v_state, v_snapshot_ref, p_created_by, p_is_automatic
    )
    RETURNING id INTO v_point_id;

    RETURN v_point_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.create_rollback_point IS 'Создание именованной точки отката';

-- -----------------------------------------------------------------------------
-- 8. Представления для аудита и версионности
-- -----------------------------------------------------------------------------

-- 8.1. Актуальные версии сущностей
CREATE OR REPLACE VIEW iktk.v_current_versions AS
SELECT
    entity_type,
    entity_id,
    project_id,
    version,
    version_number,
    state,
    change_type,
    created_by,
    created_at
FROM iktk.entity_versions
WHERE is_current = TRUE;

COMMENT ON VIEW iktk.v_current_versions IS 'Актуальные версии сущностей';

-- 8.2. История изменений проекта
CREATE OR REPLACE VIEW iktk.v_project_version_history AS
SELECT
    p.id            AS project_id,
    p.designation   AS project_designation,
    ev.version,
    ev.version_number,
    ev.state,
    ev.change_type,
    ev.created_by,
    ev.created_at,
    ev.is_current
FROM iktk.projects p
JOIN iktk.entity_versions ev
    ON ev.entity_type = 'PROJECT'
   AND ev.entity_id   = p.id
ORDER BY p.id, ev.version_number DESC;

COMMENT ON VIEW iktk.v_project_version_history IS 'История версий проекта';

-- 8.3. Сводка по точкам отката
CREATE OR REPLACE VIEW iktk.v_rollback_summary AS
SELECT
    rp.project_id,
    p.designation AS project_designation,
    rp.id         AS rollback_point_id,
    rp.name       AS rollback_point_name,
    rp.state,
    rp.created_by,
    rp.created_at,
    rp.is_automatic,
    (SELECT COUNT(*) FROM iktk.rollback_history rh
      WHERE rh.rollback_point_id = rp.id) AS rollback_count
FROM iktk.rollback_points rp
JOIN iktk.projects p ON p.id = rp.project_id
ORDER BY rp.project_id, rp.created_at DESC;

COMMENT ON VIEW iktk.v_rollback_summary IS 'Сводка по точкам отката';

-- -----------------------------------------------------------------------------
-- 9. Политики хранения (retention)
-- -----------------------------------------------------------------------------

-- Функция очистки старых версий (для планового задания).
-- По умолчанию удаляются версии старше 365 дней, кроме актуальных
-- и тех, на которые ссылаются точки отката.

CREATE OR REPLACE FUNCTION iktk.cleanup_old_versions(
    p_retention_days INT DEFAULT 365
)
RETURNS INT AS $$
DECLARE
    v_deleted INT;
BEGIN
    WITH deleted AS (
        DELETE FROM iktk.entity_versions
         WHERE created_at < NOW() - (p_retention_days || ' days')::INTERVAL
           AND is_current = FALSE
           AND id NOT IN (
               SELECT snapshot_ref FROM iktk.rollback_points
                WHERE snapshot_ref IS NOT NULL
           )
        RETURNING id
    )
    SELECT COUNT(*) INTO v_deleted FROM deleted;

    RETURN v_deleted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.cleanup_old_versions IS 'Очистка старых версий (retention policy)';

-- -----------------------------------------------------------------------------
-- 10. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V3__versioning.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V3__versioning.sql
-- =============================================================================