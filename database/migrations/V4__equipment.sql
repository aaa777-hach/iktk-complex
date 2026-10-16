-- =============================================================================
-- ИКТК: таблицы оборудования предприятия
-- Миграция: V4__equipment.sql
-- Зависит от: V1__init_schema.sql, V2__project_tables.sql, V3__versioning.sql
-- Назначение: реестр оборудования, возможности, состояние, совместимость
--             с оснасткой, требования к оборудованию и gap-анализ.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Типы (ENUM)
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'equipment_status') THEN
        CREATE TYPE iktk.equipment_status AS ENUM (
            'AVAILABLE',      -- доступно
            'BUSY',           -- занято
            'MAINTENANCE',    -- на обслуживании
            'DECOMMISSIONED'  -- списано
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'equipment_source') THEN
        CREATE TYPE iktk.equipment_source AS ENUM (
            'MANUAL',         -- введено вручную
            'IMPORT',         -- импортировано из файла
            'API',            -- получено по API
            'INTEGRATION'     -- интеграция с внешней системой
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'gap_severity') THEN
        CREATE TYPE iktk.gap_severity AS ENUM (
            'CRITICAL',       -- критичный
            'MAJOR',          -- важный
            'MINOR'           -- незначительный
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'gap_status') THEN
        CREATE TYPE iktk.gap_status AS ENUM (
            'RUNNING',        -- выполняется
            'COMPLETED',      -- завершён
            'FAILED'          -- ошибка
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'recommendation_type') THEN
        CREATE TYPE iktk.recommendation_type AS ENUM (
            'ALTERNATIVE',    -- альтернатива на предприятии
            'PURCHASE',       -- докупка
            'RENT',           -- аренда
            'OUTSOURCE',      -- аутсорс
            'ADJUST_PROCESS'  -- корректировка ТП
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'recommendation_status') THEN
        CREATE TYPE iktk.recommendation_status AS ENUM (
            'PENDING',        -- ожидает решения
            'ACCEPTED',       -- принята
            'REJECTED'        -- отклонена
        );
    END IF;
END $$;

COMMENT ON TYPE iktk.equipment_status      IS 'Статус оборудования';
COMMENT ON TYPE iktk.equipment_source      IS 'Источник данных оборудования';
COMMENT ON TYPE iktk.gap_severity          IS 'Критичность дефицита';
COMMENT ON TYPE iktk.gap_status            IS 'Статус gap-анализа';
COMMENT ON TYPE iktk.recommendation_type   IS 'Тип рекомендации';
COMMENT ON TYPE iktk.recommendation_status IS 'Статус рекомендации';

-- -----------------------------------------------------------------------------
-- 2. Основная таблица оборудования
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment (
    id                  UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    name                VARCHAR(255) NOT NULL,
    equipment_type      VARCHAR(64)  NOT NULL,
    model               VARCHAR(128),
    manufacturer        VARCHAR(128),
    inventory_no        VARCHAR(64) UNIQUE,
    serial_no           VARCHAR(64),
    manufacture_year    INT,
    enterprise_code     VARCHAR(64) REFERENCES iktk_ref.enterprises(code),
    department_code     VARCHAR(64) REFERENCES iktk_ref.departments(code),
    location            VARCHAR(255),
    status              iktk.equipment_status NOT NULL DEFAULT 'AVAILABLE',
    responsible         VARCHAR(128),
    commissioned_at     TIMESTAMPTZ,
    decommissioned_at   TIMESTAMPTZ,
    source              iktk.equipment_source NOT NULL DEFAULT 'MANUAL',
    external_id         VARCHAR(128),
    parameters          JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.equipment IS 'Реестр оборудования предприятия';
COMMENT ON COLUMN iktk.equipment.equipment_type IS 'Тип: TURNING, MILLING, PRESS, CASTING, ...';
COMMENT ON COLUMN iktk.equipment.inventory_no IS 'Инвентарный номер (уникальный)';
COMMENT ON COLUMN iktk.equipment.external_id IS 'Идентификатор во внешней системе';
COMMENT ON COLUMN iktk.equipment.parameters IS 'Произвольные параметры (JSONB)';

CREATE INDEX IF NOT EXISTS idx_equipment_type          ON iktk.equipment(equipment_type);
CREATE INDEX IF NOT EXISTS idx_equipment_status        ON iktk.equipment(status);
CREATE INDEX IF NOT EXISTS idx_equipment_inventory_no  ON iktk.equipment(inventory_no);
CREATE INDEX IF NOT EXISTS idx_equipment_enterprise    ON iktk.equipment(enterprise_code);
CREATE INDEX IF NOT EXISTS idx_equipment_department    ON iktk.equipment(department_code);
CREATE INDEX IF NOT EXISTS idx_equipment_location      ON iktk.equipment(location);
CREATE INDEX IF NOT EXISTS idx_equipment_external      ON iktk.equipment(external_id);
CREATE INDEX IF NOT EXISTS idx_equipment_name_trgm     ON iktk.equipment USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_equipment_created_at    ON iktk.equipment(created_at DESC);

CREATE TRIGGER trg_equipment_updated_at
    BEFORE UPDATE ON iktk.equipment
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 3. Возможности оборудования
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_capabilities (
    id                  UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    equipment_id        UUID NOT NULL REFERENCES iktk.equipment(id) ON DELETE CASCADE,
    operation_type      VARCHAR(64) NOT NULL,
    operation_name      VARCHAR(255),
    operation_category  VARCHAR(32),
    tolerance           VARCHAR(32),
    accuracy_grade      INT,
    max_dimensions      VARCHAR(64),
    min_dimensions      VARCHAR(64),
    max_diameter_mm     NUMERIC(12,3),
    max_length_mm       NUMERIC(12,3),
    max_mass_kg         NUMERIC(12,3),
    power_kw            NUMERIC(10,3),
    force_kn            NUMERIC(12,3),
    temperature_c       NUMERIC(8,2),
    spindle_speed_min_rpm INT,
    spindle_speed_max_rpm INT,
    feed_min            NUMERIC(10,3),
    feed_max            NUMERIC(10,3),
    productivity_per_hour NUMERIC(10,2),
    cnc_capable         BOOLEAN NOT NULL DEFAULT FALSE,
    axes_count          INT,
    cnc_system          VARCHAR(64),
    parameters          JSONB,
    comment             VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_capability_equipment_operation
        UNIQUE (equipment_id, operation_type)
);

COMMENT ON TABLE  iktk.equipment_capabilities IS 'Возможности оборудования по операциям';
COMMENT ON COLUMN iktk.equipment_capabilities.operation_type IS 'Тип операции (TURNING, MILLING, ...)';
COMMENT ON COLUMN iktk.equipment_capabilities.tolerance IS 'Достижимая точность (IT7, ±0.01 мм)';
COMMENT ON COLUMN iktk.equipment_capabilities.cnc_capable IS 'Поддержка ЧПУ';

CREATE INDEX IF NOT EXISTS idx_cap_equipment      ON iktk.equipment_capabilities(equipment_id);
CREATE INDEX IF NOT EXISTS idx_cap_operation      ON iktk.equipment_capabilities(operation_type);
CREATE INDEX IF NOT EXISTS idx_cap_cnc            ON iktk.equipment_capabilities(cnc_capable) WHERE cnc_capable = TRUE;
CREATE INDEX IF NOT EXISTS idx_cap_accuracy       ON iktk.equipment_capabilities(accuracy_grade);

CREATE TRIGGER trg_cap_updated_at
    BEFORE UPDATE ON iktk.equipment_capabilities
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 4. Материалы, поддерживаемые возможностями
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_capability_materials (
    capability_id   UUID NOT NULL REFERENCES iktk.equipment_capabilities(id) ON DELETE CASCADE,
    material_code   VARCHAR(64) NOT NULL,
    PRIMARY KEY (capability_id, material_code)
);

COMMENT ON TABLE iktk.equipment_capability_materials IS 'Материалы, поддерживаемые возможностями оборудования';

CREATE INDEX IF NOT EXISTS idx_cap_materials_code
    ON iktk.equipment_capability_materials(material_code);

-- -----------------------------------------------------------------------------
-- 5. Состояние и загрузка
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_status (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    equipment_id    UUID NOT NULL UNIQUE REFERENCES iktk.equipment(id) ON DELETE CASCADE,
    state           iktk.equipment_status NOT NULL DEFAULT 'AVAILABLE',
    load_percent    INT NOT NULL DEFAULT 0 CHECK (load_percent BETWEEN 0 AND 100),
    schedule        VARCHAR(64),
    last_used_at    TIMESTAMPTZ,
    next_maintenance_at TIMESTAMPTZ,
    notes           VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.equipment_status IS 'Текущее состояние и загрузка оборудования';
COMMENT ON COLUMN iktk.equipment_status.load_percent IS 'Процент загрузки (0–100)';
COMMENT ON COLUMN iktk.equipment_status.schedule IS 'График работы';

CREATE INDEX IF NOT EXISTS idx_status_equipment  ON iktk.equipment_status(equipment_id);
CREATE INDEX IF NOT EXISTS idx_status_state      ON iktk.equipment_status(state);
CREATE INDEX IF NOT EXISTS idx_status_load       ON iktk.equipment_status(load_percent DESC);
CREATE INDEX IF NOT EXISTS idx_status_next_maint ON iktk.equipment_status(next_maintenance_at)
    WHERE next_maintenance_at IS NOT NULL;

CREATE TRIGGER trg_status_updated_at
    BEFORE UPDATE ON iktk.equipment_status
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 6. Совместимость оборудования и оснастки
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_tooling (
    equipment_id    UUID NOT NULL REFERENCES iktk.equipment(id) ON DELETE CASCADE,
    tooling_id      UUID NOT NULL REFERENCES iktk.tooling(id) ON DELETE CASCADE,
    compatibility   VARCHAR(32) NOT NULL DEFAULT 'FULL',
    notes           VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (equipment_id, tooling_id)
);

COMMENT ON TABLE  iktk.equipment_tooling IS 'Совместимость оборудования и оснастки';
COMMENT ON COLUMN iktk.equipment_tooling.compatibility IS 'FULL, PARTIAL, CONVERSION_REQUIRED';

CREATE INDEX IF NOT EXISTS idx_equip_tooling_tooling
    ON iktk.equipment_tooling(tooling_id);

-- -----------------------------------------------------------------------------
-- 7. Требования к оборудованию (из ТП)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_requirements (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id            UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    process_id            UUID REFERENCES iktk.technology_processes(id) ON DELETE SET NULL,
    stage_id              UUID REFERENCES iktk.stages(id) ON DELETE SET NULL,
    operation_id          UUID,
    analysis_id           UUID,
    operation_type        VARCHAR(64) NOT NULL,
    required_equipment_type VARCHAR(64),
    required_accuracy     VARCHAR(32),
    max_dimensions        VARCHAR(64),
    min_dimensions        VARCHAR(64),
    material              VARCHAR(128),
    required_power_kw     NUMERIC(10,3),
    required_force_kn     NUMERIC(12,3),
    required_cnc          BOOLEAN NOT NULL DEFAULT FALSE,
    required_axes         INT,
    required_temperature_c NUMERIC(8,2),
    quantity              INT NOT NULL DEFAULT 1,
    priority              INT NOT NULL DEFAULT 0 CHECK (priority BETWEEN 0 AND 10),
    notes                 VARCHAR(1000),
    parameters            JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.equipment_requirements IS 'Требования к оборудованию из операций ТП';
COMMENT ON COLUMN iktk.equipment_requirements.priority IS 'Приоритет: 0–3 обычный, 4–7 важный, 8–10 критичный';

CREATE INDEX IF NOT EXISTS idx_req_project        ON iktk.equipment_requirements(project_id);
CREATE INDEX IF NOT EXISTS idx_req_process        ON iktk.equipment_requirements(process_id);
CREATE INDEX IF NOT EXISTS idx_req_stage          ON iktk.equipment_requirements(stage_id);
CREATE INDEX IF NOT EXISTS idx_req_analysis       ON iktk.equipment_requirements(analysis_id);
CREATE INDEX IF NOT EXISTS idx_req_operation_type ON iktk.equipment_requirements(operation_type);
CREATE INDEX IF NOT EXISTS idx_req_equipment_type ON iktk.equipment_requirements(required_equipment_type);
CREATE INDEX IF NOT EXISTS idx_req_material       ON iktk.equipment_requirements(material);
CREATE INDEX IF NOT EXISTS idx_req_cnc            ON iktk.equipment_requirements(required_cnc) WHERE required_cnc = TRUE;
CREATE INDEX IF NOT EXISTS idx_req_priority       ON iktk.equipment_requirements(priority DESC);
CREATE INDEX IF NOT EXISTS idx_req_created_at     ON iktk.equipment_requirements(created_at DESC);

CREATE TRIGGER trg_req_updated_at
    BEFORE UPDATE ON iktk.equipment_requirements
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 8. Gap-анализ
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_gap_analysis (
    id                     UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id             UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    strategy               VARCHAR(32) NOT NULL DEFAULT 'STRICT',
    status                 iktk.gap_status NOT NULL DEFAULT 'RUNNING',
    requested_by           VARCHAR(128),
    gaps_count             INT NOT NULL DEFAULT 0,
    recommendations_count  INT NOT NULL DEFAULT 0,
    gaps                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary                JSONB,
    started_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at           TIMESTAMPTZ,
    error                  VARCHAR(2000),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.equipment_gap_analysis IS 'Результаты gap-анализа по проекту';
COMMENT ON COLUMN iktk.equipment_gap_analysis.strategy IS 'STRICT, FLEXIBLE, HYBRID';
COMMENT ON COLUMN iktk.equipment_gap_analysis.gaps IS 'Список дефицитов (JSONB)';

CREATE INDEX IF NOT EXISTS idx_gap_project    ON iktk.equipment_gap_analysis(project_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_gap_strategy   ON iktk.equipment_gap_analysis(strategy);
CREATE INDEX IF NOT EXISTS idx_gap_status     ON iktk.equipment_gap_analysis(status);
CREATE INDEX IF NOT EXISTS idx_gap_started_at ON iktk.equipment_gap_analysis(started_at DESC);

CREATE TRIGGER trg_gap_updated_at
    BEFORE UPDATE ON iktk.equipment_gap_analysis
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 9. Представления
-- -----------------------------------------------------------------------------

-- 9.1. Сводка по оборудованию
CREATE OR REPLACE VIEW iktk.v_equipment_summary AS
SELECT
    e.id,
    e.name,
    e.equipment_type,
    e.model,
    e.inventory_no,
    e.status,
    e.location,
    s.load_percent,
    s.schedule,
    (SELECT COUNT(*) FROM iktk.equipment_capabilities c
      WHERE c.equipment_id = e.id) AS capabilities_count,
    (SELECT COUNT(*) FROM iktk.equipment_capabilities c
      WHERE c.equipment_id = e.id AND c.cnc_capable = TRUE) AS cnc_capabilities_count,
    e.created_at,
    e.updated_at
FROM iktk.equipment e
LEFT JOIN iktk.equipment_status s ON s.equipment_id = e.id;

COMMENT ON VIEW iktk.v_equipment_summary IS 'Сводка по оборудованию';

-- 9.2. Оборудование с возможностями (уплощённо)
CREATE OR REPLACE VIEW iktk.v_equipment_capabilities AS
SELECT
    e.id            AS equipment_id,
    e.name          AS equipment_name,
    e.equipment_type,
    e.status,
    c.id            AS capability_id,
    c.operation_type,
    c.operation_name,
    c.tolerance,
    c.max_dimensions,
    c.power_kw,
    c.force_kn,
    c.cnc_capable,
    c.axes_count,
    c.cnc_system
FROM iktk.equipment e
JOIN iktk.equipment_capabilities c ON c.equipment_id = e.id;

COMMENT ON VIEW iktk.v_equipment_capabilities IS 'Оборудование с возможностями';

-- 9.3. Требования с привязкой к проектам
CREATE OR REPLACE VIEW iktk.v_equipment_requirements AS
SELECT
    r.id,
    r.project_id,
    p.designation AS project_designation,
    r.process_id,
    r.stage_id,
    r.operation_type,
    r.required_accuracy,
    r.max_dimensions,
    r.material,
    r.required_power_kw,
    r.required_cnc,
    r.quantity,
    r.priority,
    r.analysis_id,
    r.created_at
FROM iktk.equipment_requirements r
JOIN iktk.projects p ON p.id = r.project_id;

COMMENT ON VIEW iktk.v_equipment_requirements IS 'Требования к оборудованию с привязкой к проектам';

-- 9.4. Дефициты по проектам
CREATE OR REPLACE VIEW iktk.v_gaps_by_project AS
SELECT
    a.id            AS analysis_id,
    a.project_id,
    p.designation   AS project_designation,
    a.strategy,
    a.status,
    a.gaps_count,
    a.recommendations_count,
    a.started_at,
    a.completed_at
FROM iktk.equipment_gap_analysis a
JOIN iktk.projects p ON p.id = a.project_id
ORDER BY a.started_at DESC;

COMMENT ON VIEW iktk.v_gaps_by_project IS 'Дефициты по проектам';

-- -----------------------------------------------------------------------------
-- 10. Триггеры версионности
-- -----------------------------------------------------------------------------

-- Версионность оборудования
CREATE OR REPLACE FUNCTION iktk.on_equipment_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'EQUIPMENT'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'EQUIPMENT'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'EQUIPMENT', NEW.id, NULL, v_version_label, v_version_number,
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
            'EQUIPMENT', NEW.id, NULL, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_equipment_change() IS 'Триггер версионности оборудования';

CREATE TRIGGER trg_equipment_versioning
    AFTER INSERT OR UPDATE ON iktk.equipment
    FOR EACH ROW EXECUTE FUNCTION iktk.on_equipment_change();

-- -----------------------------------------------------------------------------
-- 11. Начальные данные: типы операций
-- -----------------------------------------------------------------------------

INSERT INTO iktk_ref.operation_catalog (code, name, operation_type, description) VALUES
    ('TURNING',          'Точение',              'MACHINING',         'Токарная обработка'),
    ('MILLING',          'Фрезерование',         'MACHINING',         'Фрезерная обработка'),
    ('DRILLING',         'Сверление',            'MACHINING',         'Сверлильная обработка'),
    ('BORING',           'Растачивание',         'MACHINING',         'Расточная обработка'),
    ('GRINDING',         'Шлифование',           'MACHINING',         'Шлифовальная обработка'),
    ('CNC_MACHINING',    'Обработка на ЧПУ',     'CNC_MACHINING',     'Механообработка на станках с ЧПУ'),
    ('CASTING',          'Литьё',                'CASTING',           'Литейное производство'),
    ('HOT_FORGING',      'Горячая штамповка',    'HOT_FORGING',       'Объёмная горячая штамповка'),
    ('COLD_FORGING',     'Холодная штамповка',   'COLD_FORGING',      'Объёмная холодная штамповка'),
    ('SHEET_STAMPING',   'Листовая штамповка',   'SHEET_STAMPING',    'Листовая штамповка'),
    ('ASSEMBLY',         'Сборка',               'ASSEMBLY',          'Сборочные операции'),
    ('WELDING',          'Сварка',               'ASSEMBLY',          'Сварочные операции'),
    ('HEAT_TREATMENT',   'Термообработка',       'HEAT_TREATMENT',    'Термическая обработка'),
    ('COATING',          'Нанесение покрытий',   'COATING',           'Нанесение покрытий'),
    ('TECHNICAL_CONTROL','Технический контроль', 'TECHNICAL_CONTROL', 'Контроль качества')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 12. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V4__equipment.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V4__equipment.sql
-- =============================================================================