-- =============================================================================
-- ИКТК: таблицы BOM (Bill of Materials) и ТП сборки
-- Миграция: V5__bom.sql
-- Зависит от: V1__init_schema.sql, V2__project_tables.sql,
--             V3__versioning.sql, V4__equipment.sql
-- Назначение: сборочные единицы, позиции состава изделия, связи и
--             сопряжения, ТП сборки, операции сборки, оснастка.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Типы (ENUM)
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'bom_item_type') THEN
        CREATE TYPE iktk.bom_item_type AS ENUM (
            'PART',       -- деталь
            'ASSEMBLY',   -- сборочная единица (подсборка)
            'STANDARD',   -- стандартное изделие (ГОСТ)
            'FASTENER',   -- крепёж
            'MATERIAL'    -- материал
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_status') THEN
        CREATE TYPE iktk.assembly_status AS ENUM (
            'DRAFT',      -- черновик
            'ACTIVE',     -- активный
            'APPROVED',   -- утверждён
            'ARCHIVED'    -- архивный
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_connection_type') THEN
        CREATE TYPE iktk.assembly_connection_type AS ENUM (
            'FIXED',        -- фиксированное
            'ROTATIONAL',   -- вращательное
            'SLIDING',      -- поступательное
            'WELDED',       -- сварное
            'THREADED',     -- резьбовое
            'GLUED',        -- клеевое
            'PRESSED',      -- прессовое
            'RIVETED',      -- заклёпочное
            'PINNED',       -- штифтовое
            'KEYED',        -- шпоночное
            'SPLINED',      -- шлицевое
            'OTHER'         -- прочее
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_process_status') THEN
        CREATE TYPE iktk.assembly_process_status AS ENUM (
            'DRAFT',      -- черновик
            'APPROVED',   -- утверждён
            'REJECTED',   -- отклонён
            'ARCHIVED'    -- архивный
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_operation_type') THEN
        CREATE TYPE iktk.assembly_operation_type AS ENUM (
            'INSTALL',        -- установка детали
            'SUBASSEMBLY',    -- установка подсборки
            'FASTEN',         -- крепление
            'MATERIAL',       -- нанесение материала
            'CONTROL',        -- контроль
            'ADJUST',         -- регулировка
            'OTHER'           -- прочее
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_tooling_type') THEN
        CREATE TYPE iktk.assembly_tooling_type AS ENUM (
            'FIXTURE',    -- приспособление
            'CLAMP',      -- зажим
            'SUPPORT',    -- опора
            'LOCATOR',    -- фиксатор
            'LIFTING',    -- грузозахватное
            'MEASURING',  -- измерительное
            'TOOL',       -- сборочный инструмент
            'OTHER'       -- прочее
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'assembly_tooling_status') THEN
        CREATE TYPE iktk.assembly_tooling_status AS ENUM (
            'DRAFT',      -- черновик
            'APPROVED',   -- утверждён
            'REJECTED',   -- отклонён
            'ARCHIVED'    -- архивный
        );
    END IF;
END $$;

COMMENT ON TYPE iktk.bom_item_type             IS 'Тип позиции BOM';
COMMENT ON TYPE iktk.assembly_status           IS 'Статус сборочной единицы';
COMMENT ON TYPE iktk.assembly_connection_type  IS 'Тип связи/сопряжения';
COMMENT ON TYPE iktk.assembly_process_status   IS 'Статус ТП сборки';
COMMENT ON TYPE iktk.assembly_operation_type   IS 'Тип операции сборки';
COMMENT ON TYPE iktk.assembly_tooling_type     IS 'Тип сборочной оснастки';
COMMENT ON TYPE iktk.assembly_tooling_status   IS 'Статус сборочной оснастки';

-- -----------------------------------------------------------------------------
-- 2. Сборочные единицы
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.assembly_units (
    id               UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id       UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    parent_id        UUID REFERENCES iktk.assembly_units(id) ON DELETE CASCADE,
    designation      VARCHAR(128) NOT NULL,
    name             VARCHAR(255),
    description      VARCHAR(2000),
    geometry_id      VARCHAR(64),
    cad_system       VARCHAR(32),
    item_count       INT NOT NULL DEFAULT 0 CHECK (item_count >= 0),
    max_depth        INT NOT NULL DEFAULT 0 CHECK (max_depth >= 0),
    total_mass_kg    NUMERIC(14,4),
    version          INT NOT NULL DEFAULT 1 CHECK (version > 0),
    status           iktk.assembly_status NOT NULL DEFAULT 'DRAFT',
    updated_by       VARCHAR(128),
    parameters       JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.assembly_units IS 'Сборочные единицы (корневые и вложенные)';
COMMENT ON COLUMN iktk.assembly_units.parent_id IS 'Родительская сборка (NULL — корневая)';
COMMENT ON COLUMN iktk.assembly_units.geometry_id IS 'CAD-геометрия сборки';
COMMENT ON COLUMN iktk.assembly_units.item_count IS 'Число позиций BOM';
COMMENT ON COLUMN iktk.assembly_units.max_depth IS 'Максимальная глубина дерева';

CREATE INDEX IF NOT EXISTS idx_assembly_project     ON iktk.assembly_units(project_id);
CREATE INDEX IF NOT EXISTS idx_assembly_parent      ON iktk.assembly_units(parent_id);
CREATE INDEX IF NOT EXISTS idx_assembly_designation ON iktk.assembly_units(designation);
CREATE INDEX IF NOT EXISTS idx_assembly_status      ON iktk.assembly_units(status);
CREATE INDEX IF NOT EXISTS idx_assembly_geometry    ON iktk.assembly_units(geometry_id);
CREATE INDEX IF NOT EXISTS idx_assembly_root        ON iktk.assembly_units(project_id)
    WHERE parent_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_assembly_created_at  ON iktk.assembly_units(created_at DESC);

CREATE TRIGGER trg_assembly_updated_at
    BEFORE UPDATE ON iktk.assembly_units
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 3. Позиции BOM
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.bom_items (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    assembly_id     UUID NOT NULL REFERENCES iktk.assembly_units(id) ON DELETE CASCADE,
    parent_id       UUID REFERENCES iktk.bom_items(id) ON DELETE CASCADE,
    item_order      INT NOT NULL DEFAULT 0,
    level           INT NOT NULL DEFAULT 0 CHECK (level >= 0),
    position        VARCHAR(32),
    designation     VARCHAR(128),
    name            VARCHAR(255),
    quantity        INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit            VARCHAR(32) DEFAULT 'шт',
    material        VARCHAR(128),
    item_type       iktk.bom_item_type NOT NULL DEFAULT 'PART',
    geometry_id     VARCHAR(64),
    cad_system      VARCHAR(32),
    mass_kg         NUMERIC(12,4),
    note            VARCHAR(1000),
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.bom_items IS 'Позиции состава изделия (BOM)';
COMMENT ON COLUMN iktk.bom_items.parent_id IS 'Родительская позиция (иерархия)';
COMMENT ON COLUMN iktk.bom_items.item_order IS 'Порядок в дереве';
COMMENT ON COLUMN iktk.bom_items.level IS 'Уровень вложенности (0 — корень)';
COMMENT ON COLUMN iktk.bom_items.position IS 'Номер позиции по ГОСТ';

CREATE INDEX IF NOT EXISTS idx_bom_items_assembly     ON iktk.bom_items(assembly_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_parent       ON iktk.bom_items(parent_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_order        ON iktk.bom_items(assembly_id, item_order);
CREATE INDEX IF NOT EXISTS idx_bom_items_type         ON iktk.bom_items(item_type);
CREATE INDEX IF NOT EXISTS idx_bom_items_designation  ON iktk.bom_items(designation);
CREATE INDEX IF NOT EXISTS idx_bom_items_material     ON iktk.bom_items(material);
CREATE INDEX IF NOT EXISTS idx_bom_items_level        ON iktk.bom_items(assembly_id, level);
CREATE INDEX IF NOT EXISTS idx_bom_items_geometry     ON iktk.bom_items(geometry_id);

CREATE TRIGGER trg_bom_items_updated_at
    BEFORE UPDATE ON iktk.bom_items
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 4. Связи и сопряжения сборки
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.assembly_connections (
    id                  UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    assembly_id         UUID NOT NULL REFERENCES iktk.assembly_units(id) ON DELETE CASCADE,
    connection_type     iktk.assembly_connection_type NOT NULL,
    connection_name     VARCHAR(255),
    description         VARCHAR(1000),
    part1_id            UUID NOT NULL REFERENCES iktk.bom_items(id) ON DELETE CASCADE,
    part2_id            UUID NOT NULL REFERENCES iktk.bom_items(id) ON DELETE CASCADE,
    part1_designation   VARCHAR(128),
    part2_designation   VARCHAR(128),
    connection_order    INT NOT NULL DEFAULT 0,
    needs_tool          BOOLEAN NOT NULL DEFAULT FALSE,
    needs_fixture       BOOLEAN NOT NULL DEFAULT FALSE,
    needs_control       BOOLEAN NOT NULL DEFAULT FALSE,
    tool_type           VARCHAR(64),
    fixture_type        VARCHAR(64),
    diameter_mm         NUMERIC(12,3),
    clearance_mm        NUMERIC(12,4),
    interference_mm     NUMERIC(12,4),
    torque_nm           NUMERIC(12,3),
    press_force_kn      NUMERIC(12,3),
    weld_length_mm      NUMERIC(12,3),
    weld_leg_mm         NUMERIC(12,3),
    parameters          JSONB,
    note                VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_conn_parts CHECK (part1_id <> part2_id)
);

COMMENT ON TABLE  iktk.assembly_connections IS 'Связи и сопряжения сборочной единицы';
COMMENT ON COLUMN iktk.assembly_connections.part1_id IS 'Первая деталь (базовая)';
COMMENT ON COLUMN iktk.assembly_connections.part2_id IS 'Вторая деталь (устанавливаемая)';
COMMENT ON COLUMN iktk.assembly_connections.connection_order IS 'Рекомендуемый порядок установки';

CREATE INDEX IF NOT EXISTS idx_conn_assembly ON iktk.assembly_connections(assembly_id);
CREATE INDEX IF NOT EXISTS idx_conn_part1    ON iktk.assembly_connections(part1_id);
CREATE INDEX IF NOT EXISTS idx_conn_part2    ON iktk.assembly_connections(part2_id);
CREATE INDEX IF NOT EXISTS idx_conn_type     ON iktk.assembly_connections(connection_type);
CREATE INDEX IF NOT EXISTS idx_conn_order    ON iktk.assembly_connections(assembly_id, connection_order);

CREATE TRIGGER trg_conn_updated_at
    BEFORE UPDATE ON iktk.assembly_connections
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 5. ТП сборки
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.assembly_processes (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    bom_id            UUID REFERENCES iktk.assembly_units(id) ON DELETE SET NULL,
    designation       VARCHAR(128) NOT NULL,
    name              VARCHAR(255),
    version           INT NOT NULL DEFAULT 1 CHECK (version > 0),
    status            iktk.assembly_process_status NOT NULL DEFAULT 'DRAFT',
    strategy          VARCHAR(32),
    operations_count  INT NOT NULL DEFAULT 0,
    tooling_count     INT NOT NULL DEFAULT 0,
    approved_by       VARCHAR(128),
    approved_at       TIMESTAMPTZ,
    parameters        JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.assembly_processes IS 'Технологические процессы сборки';
COMMENT ON COLUMN iktk.assembly_processes.strategy IS 'TOPOLOGICAL, GREEDY, MANUAL';
COMMENT ON COLUMN iktk.assembly_processes.operations_count IS 'Число операций сборки';

CREATE INDEX IF NOT EXISTS idx_asm_process_project    ON iktk.assembly_processes(project_id);
CREATE INDEX IF NOT EXISTS idx_asm_process_bom        ON iktk.assembly_processes(bom_id);
CREATE INDEX IF NOT EXISTS idx_asm_process_status     ON iktk.assembly_processes(status);
CREATE INDEX IF NOT EXISTS idx_asm_process_strategy   ON iktk.assembly_processes(strategy);
CREATE INDEX IF NOT EXISTS idx_asm_process_created_at ON iktk.assembly_processes(created_at DESC);

CREATE TRIGGER trg_asm_process_updated_at
    BEFORE UPDATE ON iktk.assembly_processes
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 6. Операции сборки
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.assembly_operations (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    process_id        UUID NOT NULL REFERENCES iktk.assembly_processes(id) ON DELETE CASCADE,
    operation_order   INT NOT NULL DEFAULT 1,
    designation       VARCHAR(64),
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(1000),
    operation_type    iktk.assembly_operation_type NOT NULL DEFAULT 'INSTALL',
    bom_item_id       UUID REFERENCES iktk.bom_items(id) ON DELETE SET NULL,
    bom_item_designation VARCHAR(128),
    equipment_id      VARCHAR(64),
    tooling_id        UUID REFERENCES iktk.tooling(id) ON DELETE SET NULL,
    time_norm_min     NUMERIC(12,3),
    setup_time_min    NUMERIC(12,3),
    work_grade        INT,
    required_accuracy VARCHAR(32),
    comment           VARCHAR(1000),
    parameters        JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asm_operation_order UNIQUE (process_id, operation_order)
);

COMMENT ON TABLE  iktk.assembly_operations IS 'Операции ТП сборки';
COMMENT ON COLUMN iktk.assembly_operations.bom_item_id IS 'Позиция BOM, к которой относится операция';

CREATE INDEX IF NOT EXISTS idx_asm_op_process    ON iktk.assembly_operations(process_id, operation_order);
CREATE INDEX IF NOT EXISTS idx_asm_op_type       ON iktk.assembly_operations(operation_type);
CREATE INDEX IF NOT EXISTS idx_asm_op_bom_item   ON iktk.assembly_operations(bom_item_id);
CREATE INDEX IF NOT EXISTS idx_asm_op_equipment  ON iktk.assembly_operations(equipment_id);
CREATE INDEX IF NOT EXISTS idx_asm_op_tooling    ON iktk.assembly_operations(tooling_id);

CREATE TRIGGER trg_asm_op_updated_at
    BEFORE UPDATE ON iktk.assembly_operations
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 7. Сборочная оснастка
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.assembly_tooling (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    process_id        UUID NOT NULL REFERENCES iktk.assembly_processes(id) ON DELETE CASCADE,
    operation_id      UUID REFERENCES iktk.assembly_operations(id) ON DELETE SET NULL,
    designation       VARCHAR(128) NOT NULL,
    name              VARCHAR(255),
    tooling_type      iktk.assembly_tooling_type NOT NULL,
    material          VARCHAR(128),
    estimated_cost    NUMERIC(14,2),
    status            iktk.assembly_tooling_status NOT NULL DEFAULT 'DRAFT',
    file_uri          VARCHAR(1024),
    parameters        JSONB,
    comment           VARCHAR(1000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.assembly_tooling IS 'Сборочная оснастка и приспособления';
COMMENT ON COLUMN iktk.assembly_tooling.tooling_type IS 'FIXTURE, CLAMP, SUPPORT, ...';
COMMENT ON COLUMN iktk.assembly_tooling.estimated_cost IS 'Оценочная стоимость изготовления';

CREATE INDEX IF NOT EXISTS idx_asm_tooling_process   ON iktk.assembly_tooling(process_id);
CREATE INDEX IF NOT EXISTS idx_asm_tooling_operation ON iktk.assembly_tooling(operation_id);
CREATE INDEX IF NOT EXISTS idx_asm_tooling_type      ON iktk.assembly_tooling(tooling_type);
CREATE INDEX IF NOT EXISTS idx_asm_tooling_status    ON iktk.assembly_tooling(status);

CREATE TRIGGER trg_asm_tooling_updated_at
    BEFORE UPDATE ON iktk.assembly_tooling
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 8. Представления
-- -----------------------------------------------------------------------------

-- 8.1. Сводка по сборочным единицам
CREATE OR REPLACE VIEW iktk.v_assembly_summary AS
SELECT
    a.id,
    a.project_id,
    p.designation AS project_designation,
    a.designation,
    a.name,
    a.parent_id,
    a.item_count,
    a.max_depth,
    a.version,
    a.status,
    a.created_at,
    a.updated_at
FROM iktk.assembly_units a
JOIN iktk.projects p ON p.id = a.project_id;

COMMENT ON VIEW iktk.v_assembly_summary IS 'Сводка по сборочным единицам';

-- 8.2. Полное дерево BOM
CREATE OR REPLACE VIEW iktk.v_bom_full AS
SELECT
    bi.id,
    bi.assembly_id,
    a.designation AS assembly_designation,
    a.project_id,
    bi.parent_id,
    bi.item_order,
    bi.level,
    bi.position,
    bi.designation,
    bi.name,
    bi.quantity,
    bi.unit,
    bi.material,
    bi.item_type,
    bi.mass_kg
FROM iktk.bom_items bi
JOIN iktk.assembly_units a ON a.id = bi.assembly_id
ORDER BY a.id, bi.item_order;

COMMENT ON VIEW iktk.v_bom_full IS 'Полное дерево BOM';

-- 8.3. Связи с обозначениями деталей
CREATE OR REPLACE VIEW iktk.v_assembly_connections AS
SELECT
    c.id,
    c.assembly_id,
    c.connection_type,
    c.connection_name,
    c.part1_id,
    c.part1_designation,
    c.part2_id,
    c.part2_designation,
    c.connection_order,
    c.needs_tool,
    c.needs_fixture,
    c.needs_control
FROM iktk.assembly_connections c
ORDER BY c.assembly_id, c.connection_order;

COMMENT ON VIEW iktk.v_assembly_connections IS 'Связи и сопряжения сборки с обозначениями';

-- 8.4. Сводка по ТП сборки
CREATE OR REPLACE VIEW iktk.v_assembly_processes AS
SELECT
    ap.id,
    ap.project_id,
    ap.bom_id,
    ap.designation,
    ap.name,
    ap.version,
    ap.status,
    ap.strategy,
    ap.operations_count,
    ap.tooling_count,
    ap.approved_by,
    ap.approved_at,
    ap.created_at,
    ap.updated_at
FROM iktk.assembly_processes ap
ORDER BY ap.project_id, ap.created_at DESC;

COMMENT ON VIEW iktk.v_assembly_processes IS 'Сводка по ТП сборки';

-- -----------------------------------------------------------------------------
-- 9. Триггеры версионности
-- -----------------------------------------------------------------------------

-- Версионность сборочных единиц
CREATE OR REPLACE FUNCTION iktk.on_assembly_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'ASSEMBLY'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'ASSEMBLY'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'ASSEMBLY', NEW.id, NEW.project_id, v_version_label, v_version_number,
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
            'ASSEMBLY', NEW.id, NEW.project_id, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_assembly_change() IS 'Триггер версионности сборочных единиц';

CREATE TRIGGER trg_assembly_versioning
    AFTER INSERT OR UPDATE ON iktk.assembly_units
    FOR EACH ROW EXECUTE FUNCTION iktk.on_assembly_change();

-- Версионность ТП сборки
CREATE OR REPLACE FUNCTION iktk.on_assembly_process_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'ASSEMBLY_PROCESS'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'ASSEMBLY_PROCESS'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'ASSEMBLY_PROCESS', NEW.id, NEW.project_id, v_version_label, v_version_number,
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
            'ASSEMBLY_PROCESS', NEW.id, NEW.project_id, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_assembly_process_change() IS 'Триггер версионности ТП сборки';

CREATE TRIGGER trg_asm_process_versioning
    AFTER INSERT OR UPDATE ON iktk.assembly_processes
    FOR EACH ROW EXECUTE FUNCTION iktk.on_assembly_process_change();

-- Версионность сборочной оснастки
CREATE OR REPLACE FUNCTION iktk.on_assembly_tooling_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'ASSEMBLY_TOOLING'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'ASSEMBLY_TOOLING'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'ASSEMBLY_TOOLING', NEW.id, NULL, v_version_label, v_version_number,
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
            'ASSEMBLY_TOOLING', NEW.id, NULL, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_assembly_tooling_change() IS 'Триггер версионности сборочной оснастки';

CREATE TRIGGER trg_asm_tooling_versioning
    AFTER INSERT OR UPDATE ON iktk.assembly_tooling
    FOR EACH ROW EXECUTE FUNCTION iktk.on_assembly_tooling_change();

-- -----------------------------------------------------------------------------
-- 10. Функции обхода иерархии
-- -----------------------------------------------------------------------------

-- Получить всех потомков позиции BOM
CREATE OR REPLACE FUNCTION iktk.bom_descendants(p_item_id UUID)
RETURNS TABLE (
    id          UUID,
    parent_id   UUID,
    level       INT,
    designation VARCHAR(128)
) AS $$
BEGIN
    RETURN QUERY
    WITH RECURSIVE descendants AS (
        SELECT bi.id, bi.parent_id, bi.level, bi.designation
          FROM iktk.bom_items bi
         WHERE bi.parent_id = p_item_id
        UNION ALL
        SELECT bi.id, bi.parent_id, bi.level, bi.designation
          FROM iktk.bom_items bi
          JOIN descendants d ON bi.parent_id = d.id
    )
    SELECT * FROM descendants;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION iktk.bom_descendants(UUID) IS 'Рекурсивно получить всех потомков позиции BOM';

-- Получить всех предков позиции BOM
CREATE OR REPLACE FUNCTION iktk.bom_ancestors(p_item_id UUID)
RETURNS TABLE (
    id          UUID,
    parent_id   UUID,
    level       INT,
    designation VARCHAR(128)
) AS $$
BEGIN
    RETURN QUERY
    WITH RECURSIVE ancestors AS (
        SELECT bi.id, bi.parent_id, bi.level, bi.designation
          FROM iktk.bom_items bi
         WHERE bi.id = p_item_id
        UNION ALL
        SELECT bi.id, bi.parent_id, bi.level, bi.designation
          FROM iktk.bom_items bi
          JOIN ancestors a ON bi.id = a.parent_id
    )
    SELECT * FROM ancestors WHERE id <> p_item_id;
END;
$$ LANGUAGE plpgsql STABLE;

COMMENT ON FUNCTION iktk.bom_ancestors(UUID) IS 'Рекурсивно получить всех предков позиции BOM';

-- -----------------------------------------------------------------------------
-- 11. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V5__bom.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V5__bom.sql
-- =============================================================================