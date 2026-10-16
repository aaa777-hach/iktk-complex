-- =============================================================================
-- ИКТК: таблицы проектов, ТП, этапов и геометрии
-- Миграция: V2__project_tables.sql
-- Зависит от: V1__init_schema.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Геометрия объектов изготовления
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.geometry_models (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    object_ref        VARCHAR(128) NOT NULL,
    version           VARCHAR(64) NOT NULL DEFAULT 'v1',
    format            VARCHAR(32) NOT NULL,
    canonical_format  VARCHAR(32),
    file_uri          VARCHAR(1024) NOT NULL,
    file_hash         VARCHAR(128),
    byte_size         BIGINT,
    units             VARCHAR(16) NOT NULL DEFAULT 'mm',
    is_assembly       BOOLEAN NOT NULL DEFAULT FALSE,
    bounding_box      JSONB,
    mass_properties   JSONB,
    material_code     VARCHAR(64) REFERENCES iktk_ref.materials(code),
    source_system     VARCHAR(32),
    source_external_id VARCHAR(255),
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, object_ref, version)
);

COMMENT ON TABLE  iktk.geometry_models IS 'Геометрия объектов изготовления (детали/сборки)';
COMMENT ON COLUMN iktk.geometry_models.object_ref IS 'Ссылка на объект: деталь или сборка';
COMMENT ON COLUMN iktk.geometry_models.canonical_format IS 'Канонический формат (STEP AP242, Parasolid)';
COMMENT ON COLUMN iktk.geometry_models.file_uri IS 'Внутренний URI файла в MinIO/S3';
COMMENT ON COLUMN iktk.geometry_models.file_hash IS 'SHA-256 файла для дедупликации';
COMMENT ON COLUMN iktk.geometry_models.is_assembly IS 'Признак сборочной единицы';
COMMENT ON COLUMN iktk.geometry_models.bounding_box IS 'Габариты: {x,y,z} (JSONB)';
COMMENT ON COLUMN iktk.geometry_models.mass_properties IS 'Массовые свойства: volume, mass, area (JSONB)';

CREATE INDEX IF NOT EXISTS idx_geometry_project
    ON iktk.geometry_models(project_id);
CREATE INDEX IF NOT EXISTS idx_geometry_object_ref
    ON iktk.geometry_models(object_ref);
CREATE INDEX IF NOT EXISTS idx_geometry_hash
    ON iktk.geometry_models(file_hash);
CREATE INDEX IF NOT EXISTS idx_geometry_assembly
    ON iktk.geometry_models(is_assembly) WHERE is_assembly = TRUE;
CREATE INDEX IF NOT EXISTS idx_geometry_created
    ON iktk.geometry_models(created_at DESC);

CREATE TRIGGER trg_geometry_updated_at
    BEFORE UPDATE ON iktk.geometry_models
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 2. Технологические процессы
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.technology_processes (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    designation       VARCHAR(128) NOT NULL,
    name              VARCHAR(255),
    version           VARCHAR(64) NOT NULL DEFAULT 'v1',
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    object_ref        VARCHAR(128) NOT NULL,
    object_type       iktk.object_type NOT NULL,
    process_type      iktk.process_type NOT NULL,
    status            iktk.process_status NOT NULL DEFAULT 'DRAFT',
    production_type   iktk.production_type,
    geometry_id       UUID REFERENCES iktk.geometry_models(id),
    parent_process_id UUID REFERENCES iktk.technology_processes(id) ON DELETE SET NULL,
    author            VARCHAR(128),
    approved_by       VARCHAR(128),
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, designation, version)
);

COMMENT ON TABLE  iktk.technology_processes IS 'Технологические процессы (ТП)';
COMMENT ON COLUMN iktk.technology_processes.object_ref IS 'Ссылка на объект изготовления';
COMMENT ON COLUMN iktk.technology_processes.object_type IS 'Деталь или сборка';
COMMENT ON COLUMN iktk.technology_processes.process_type IS 'Тип ТП: литьё, штамповка, механообработка, сборка и т.д.';
COMMENT ON COLUMN iktk.technology_processes.geometry_id IS 'Ссылка на геометрию, по которой построен ТП';
COMMENT ON COLUMN iktk.technology_processes.parent_process_id IS 'Родительский ТП (для подсборок)';

CREATE INDEX IF NOT EXISTS idx_tp_project        ON iktk.technology_processes(project_id);
CREATE INDEX IF NOT EXISTS idx_tp_object         ON iktk.technology_processes(object_ref);
CREATE INDEX IF NOT EXISTS idx_tp_type           ON iktk.technology_processes(process_type);
CREATE INDEX IF NOT EXISTS idx_tp_status         ON iktk.technology_processes(status);
CREATE INDEX IF NOT EXISTS idx_tp_version        ON iktk.technology_processes(version);
CREATE INDEX IF NOT EXISTS idx_tp_geometry       ON iktk.technology_processes(geometry_id);
CREATE INDEX IF NOT EXISTS idx_tp_parent         ON iktk.technology_processes(parent_process_id);
CREATE INDEX IF NOT EXISTS idx_tp_created        ON iktk.technology_processes(created_at DESC);

CREATE TRIGGER trg_tp_updated_at
    BEFORE UPDATE ON iktk.technology_processes
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 3. Этапы (операции) ТП
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.stages (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    technology_process_id UUID NOT NULL REFERENCES iktk.technology_processes(id) ON DELETE CASCADE,
    designation           VARCHAR(64) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           VARCHAR(2000),
    stage_order           INT NOT NULL,
    operation_type        iktk.operation_type NOT NULL,
    stage_kind            iktk.stage_kind NOT NULL DEFAULT 'MAIN',
    work_grade            INT,
    equipment_id          VARCHAR(64),
    tooling_id            VARCHAR(64),
    cnc_program_id        VARCHAR(64),
    time_norm_min         NUMERIC(12,3),
    setup_time_min        NUMERIC(12,3),
    tariff_code           VARCHAR(64),
    required_accuracy     VARCHAR(32),
    max_dimensions        VARCHAR(64),
    required_power_kw     NUMERIC(10,3),
    material              VARCHAR(128),
    comment               VARCHAR(1000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (technology_process_id, stage_order)
);

COMMENT ON TABLE  iktk.stages IS 'Этапы (операции) технологического процесса';
COMMENT ON COLUMN iktk.stages.stage_order IS 'Порядковый номер этапа в ТП (синхронизирован с @OrderColumn)';
COMMENT ON COLUMN iktk.stages.equipment_id IS 'Ссылка на оборудование (equipment-service)';
COMMENT ON COLUMN iktk.stages.tooling_id IS 'Ссылка на оснастку';
COMMENT ON COLUMN iktk.stages.cnc_program_id IS 'Ссылка на программу ЧПУ';
COMMENT ON COLUMN iktk.stages.time_norm_min IS 'Штучное время, мин';
COMMENT ON COLUMN iktk.stages.setup_time_min IS 'Подготовительно-заключительное время, мин';

CREATE INDEX IF NOT EXISTS idx_stages_tp         ON iktk.stages(technology_process_id, stage_order);
CREATE INDEX IF NOT EXISTS idx_stages_operation  ON iktk.stages(operation_type);
CREATE INDEX IF NOT EXISTS idx_stages_equipment  ON iktk.stages(equipment_id);
CREATE INDEX IF NOT EXISTS idx_stages_tooling    ON iktk.stages(tooling_id);
CREATE INDEX IF NOT EXISTS idx_stages_cnc        ON iktk.stages(cnc_program_id);
CREATE INDEX IF NOT EXISTS idx_stages_kind       ON iktk.stages(stage_kind);

CREATE TRIGGER trg_stages_updated_at
    BEFORE UPDATE ON iktk.stages
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 4. Требования к оборудованию из ТП (для gap-анализа)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.stage_equipment_requirements (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    stage_id              UUID NOT NULL REFERENCES iktk.stages(id) ON DELETE CASCADE,
    requirement_type      VARCHAR(32) NOT NULL,
    required_equipment_type VARCHAR(64),
    required_accuracy     VARCHAR(32),
    required_power_kw     NUMERIC(10,3),
    max_dimensions        VARCHAR(64),
    required_materials    JSONB,
    required_operations   JSONB,
    notes                 VARCHAR(1000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.stage_equipment_requirements IS 'Требования к оборудованию, вытекающие из этапа ТП';

CREATE INDEX IF NOT EXISTS idx_ser_stage
    ON iktk.stage_equipment_requirements(stage_id);
CREATE INDEX IF NOT EXISTS idx_ser_equipment_type
    ON iktk.stage_equipment_requirements(required_equipment_type);
CREATE INDEX IF NOT EXISTS idx_ser_requirement_type
    ON iktk.stage_equipment_requirements(requirement_type);

-- -----------------------------------------------------------------------------
-- 5. Оснастка (проектная)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.tooling (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id      UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    process_id      UUID REFERENCES iktk.technology_processes(id) ON DELETE SET NULL,
    stage_id        UUID REFERENCES iktk.stages(id) ON DELETE SET NULL,
    designation     VARCHAR(128) NOT NULL,
    name            VARCHAR(255),
    tooling_type    VARCHAR(64) NOT NULL,
    material_code   VARCHAR(64) REFERENCES iktk_ref.materials(code),
    file_uri        VARCHAR(1024),
    estimated_cost  NUMERIC(14,2),
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.tooling IS 'Оснастка: модели, штампы, пресс-формы, приспособления';
COMMENT ON COLUMN iktk.tooling.tooling_type IS 'Тип оснастки: MODEL, DIE, MOLD, FIXTURE, ...';

CREATE INDEX IF NOT EXISTS idx_tooling_project
    ON iktk.tooling(project_id);
CREATE INDEX IF NOT EXISTS idx_tooling_process
    ON iktk.tooling(process_id);
CREATE INDEX IF NOT EXISTS idx_tooling_stage
    ON iktk.tooling(stage_id);
CREATE INDEX IF NOT EXISTS idx_tooling_type
    ON iktk.tooling(tooling_type);

CREATE TRIGGER trg_tooling_updated_at
    BEFORE UPDATE ON iktk.tooling
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 6. Связь этапов и оснастки (many-to-many)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.stage_tooling (
    stage_id        UUID NOT NULL REFERENCES iktk.stages(id) ON DELETE CASCADE,
    tooling_id      UUID NOT NULL REFERENCES iktk.tooling(id) ON DELETE CASCADE,
    usage_note      VARCHAR(512),
    PRIMARY KEY (stage_id, tooling_id)
);

-- -----------------------------------------------------------------------------
-- 7. Программы ЧПУ
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.cnc_programs (
    id              UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id      UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    process_id      UUID REFERENCES iktk.technology_processes(id) ON DELETE SET NULL,
    equipment_id    VARCHAR(64) NOT NULL,
    equipment_model VARCHAR(128),
    postprocessor   VARCHAR(64) NOT NULL,
    program_name    VARCHAR(255) NOT NULL,
    program_uri     VARCHAR(1024) NOT NULL,
    line_count      INT,
    byte_size       BIGINT,
    valid           BOOLEAN NOT NULL DEFAULT FALSE,
    validation_log  JSONB,
    setup_sheet     JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.cnc_programs IS 'Управляющие программы для станков с ЧПУ';
COMMENT ON COLUMN iktk.cnc_programs.program_uri IS 'URI файла УП в MinIO/S3';

CREATE INDEX IF NOT EXISTS idx_cnc_project
    ON iktk.cnc_programs(project_id);
CREATE INDEX IF NOT EXISTS idx_cnc_process
    ON iktk.cnc_programs(process_id);
CREATE INDEX IF NOT EXISTS idx_cnc_equipment
    ON iktk.cnc_programs(equipment_id);
CREATE INDEX IF NOT EXISTS idx_cnc_valid
    ON iktk.cnc_programs(valid) WHERE valid = TRUE;

CREATE TRIGGER trg_cnc_updated_at
    BEFORE UPDATE ON iktk.cnc_programs
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 8. Нормы времени
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.time_norms (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id            UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    process_id            UUID NOT NULL REFERENCES iktk.technology_processes(id) ON DELETE CASCADE,
    source                VARCHAR(64) NOT NULL,
    batch_size            NUMERIC(12,2) NOT NULL DEFAULT 1,
    stage_norms           JSONB NOT NULL,
    total_tpz             NUMERIC(12,3),
    total_tsh             NUMERIC(12,3),
    total_tsh_k           NUMERIC(12,3),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.time_norms IS 'Нормы времени по ТП';
COMMENT ON COLUMN iktk.time_norms.stage_norms IS 'Детализация по этапам (JSONB)';

CREATE INDEX IF NOT EXISTS idx_time_norms_project
    ON iktk.time_norms(project_id);
CREATE INDEX IF NOT EXISTS idx_time_norms_process
    ON iktk.time_norms(process_id);
CREATE INDEX IF NOT EXISTS idx_time_norms_source
    ON iktk.time_norms(source);

-- -----------------------------------------------------------------------------
-- 9. Нормы расхода материалов
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.material_norms (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id            UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    process_id            UUID NOT NULL REFERENCES iktk.technology_processes(id) ON DELETE CASCADE,
    material_code         VARCHAR(64) REFERENCES iktk_ref.materials(code),
    blank_mass            NUMERIC(14,4),
    part_mass             NUMERIC(14,4),
    norm_per_part         NUMERIC(14,4),
    norm_per_batch        NUMERIC(14,4),
    batch_size            NUMERIC(12,2) NOT NULL DEFAULT 1,
    material_utilization  NUMERIC(8,4),
    tech_loss_coefficient NUMERIC(8,4),
    defect_coefficient    NUMERIC(8,4),
    consumptions          JSONB,
    tool_consumptions     JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.material_norms IS 'Нормы расхода материалов по ТП';
COMMENT ON COLUMN iktk.material_norms.material_utilization IS 'КИМ = part_mass / norm_per_part';
COMMENT ON COLUMN iktk.material_norms.consumptions IS 'Пооперационные потери (JSONB)';
COMMENT ON COLUMN iktk.material_norms.tool_consumptions IS 'Расход инструмента (JSONB)';

CREATE INDEX IF NOT EXISTS idx_mat_norms_project
    ON iktk.material_norms(project_id);
CREATE INDEX IF NOT EXISTS idx_mat_norms_process
    ON iktk.material_norms(process_id);
CREATE INDEX IF NOT EXISTS idx_mat_norms_material
    ON iktk.material_norms(material_code);

-- -----------------------------------------------------------------------------
-- 10. Вспомогательные функции и представления
-- -----------------------------------------------------------------------------

-- 10.1. Полная иерархия ТП по проекту
CREATE OR REPLACE VIEW iktk.v_project_processes AS
SELECT
    p.id            AS project_id,
    p.designation   AS project_designation,
    p.name          AS project_name,
    p.object_type   AS project_object_type,
    p.status        AS project_status,
    tp.id           AS process_id,
    tp.designation  AS process_designation,
    tp.process_type AS process_type,
    tp.status       AS process_status,
    tp.version      AS process_version,
    tp.created_at   AS process_created_at
FROM iktk.projects p
LEFT JOIN iktk.technology_processes tp ON tp.project_id = p.id;

COMMENT ON VIEW iktk.v_project_processes IS 'Связь проектов и ТП';

-- 10.2. Этапы с информацией об оборудовании
CREATE OR REPLACE VIEW iktk.v_stages_with_equipment AS
SELECT
    s.id                    AS stage_id,
    s.technology_process_id AS process_id,
    s.designation           AS stage_designation,
    s.name                  AS stage_name,
    s.stage_order           AS stage_order,
    s.operation_type        AS operation_type,
    s.stage_kind            AS stage_kind,
    s.equipment_id          AS equipment_id,
    s.tooling_id            AS tooling_id,
    s.cnc_program_id        AS cnc_program_id,
    s.time_norm_min         AS time_norm_min,
    s.setup_time_min        AS setup_time_min,
    s.required_accuracy     AS required_accuracy,
    s.required_power_kw     AS required_power_kw
FROM iktk.stages s
ORDER BY s.technology_process_id, s.stage_order;

COMMENT ON VIEW iktk.v_stages_with_equipment IS 'Этапы ТП с требованиями к оборудованию';

-- 10.3. Сводные нормы по проекту
CREATE OR REPLACE VIEW iktk.v_project_norms AS
SELECT
    tn.project_id,
    tn.process_id,
    tn.total_tsh,
    tn.total_tsh_k,
    mn.norm_per_part,
    mn.norm_per_batch,
    mn.material_utilization
FROM iktk.time_norms tn
LEFT JOIN iktk.material_norms mn
    ON mn.project_id = tn.project_id
   AND mn.process_id = tn.process_id;

COMMENT ON VIEW iktk.v_project_norms IS 'Сводные нормы времени и материалов по проекту';

-- -----------------------------------------------------------------------------
-- 11. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V2__project_tables.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V2__project_tables.sql
-- =============================================================================