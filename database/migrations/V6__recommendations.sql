-- =============================================================================
-- ИКТК: таблицы рекомендаций, каталога внешнего оборудования и аутсорса
-- Миграция: V6__recommendations.sql
-- Зависит от: V1__init_schema.sql, V2__project_tables.sql,
--             V3__versioning.sql, V4__equipment.sql, V5__bom.sql
-- Назначение: рекомендации по оборудованию, каталог внешнего оборудования,
--             варианты аутсорса.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Типы (ENUM)
-- -----------------------------------------------------------------------------

-- Примечание: типы iktk.recommendation_type и iktk.recommendation_status
-- созданы в миграции V4__equipment.sql.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vendor_source') THEN
        CREATE TYPE iktk.vendor_source AS ENUM (
            'MANUAL',      -- введено вручную
            'IMPORT',      -- импортировано из файла
            'API'          -- получено по API
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'outsourcing_type') THEN
        CREATE TYPE iktk.outsourcing_type AS ENUM (
            'CONTRACTOR',     -- сторонний подрядчик
            'PARTNER_PLANT',  -- завод-партнёр
            'RENTAL',         -- аренда с оператором
            'COOPERATION'     -- кооперация
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'outsourcing_status') THEN
        CREATE TYPE iktk.outsourcing_status AS ENUM (
            'PENDING',    -- ожидает решения
            'SELECTED',   -- выбран
            'REJECTED'    -- отклонён
        );
    END IF;
END $$;

COMMENT ON TYPE iktk.vendor_source      IS 'Источник данных каталога';
COMMENT ON TYPE iktk.outsourcing_type   IS 'Вид аутсорса';
COMMENT ON TYPE iktk.outsourcing_status IS 'Статус варианта аутсорса';

-- -----------------------------------------------------------------------------
-- 2. Каталог внешнего оборудования
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.vendor_catalog (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    external_id           VARCHAR(128) UNIQUE,
    name                  VARCHAR(255) NOT NULL,
    manufacturer          VARCHAR(128),
    model                 VARCHAR(128),
    equipment_type        VARCHAR(64) NOT NULL,
    description           VARCHAR(2000),
    country               VARCHAR(64),
    specs                 JSONB,
    cnc_capable           BOOLEAN NOT NULL DEFAULT FALSE,
    max_dimensions        VARCHAR(64),
    power_kw              NUMERIC(10,3),
    force_kn              NUMERIC(12,3),
    cost                  NUMERIC(14,2),
    rental_cost_per_day   NUMERIC(14,2),
    lead_time_days        INT,
    warranty_months       INT,
    terms                 VARCHAR(1000),
    vendor_name           VARCHAR(255),
    vendor_contact        VARCHAR(255),
    vendor_region         VARCHAR(64),
    available             BOOLEAN NOT NULL DEFAULT TRUE,
    source                iktk.vendor_source NOT NULL DEFAULT 'MANUAL',
    parameters            JSONB,
    last_synced_at        TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.vendor_catalog IS 'Каталог внешнего оборудования (поставщики)';
COMMENT ON COLUMN iktk.vendor_catalog.external_id IS 'Идентификатор во внешней системе';
COMMENT ON COLUMN iktk.vendor_catalog.equipment_type IS 'Тип: TURNING, MILLING, PRESS, ...';
COMMENT ON COLUMN iktk.vendor_catalog.cost IS 'Стоимость покупки (руб.)';
COMMENT ON COLUMN iktk.vendor_catalog.rental_cost_per_day IS 'Стоимость аренды в сутки (руб.)';
COMMENT ON COLUMN iktk.vendor_catalog.lead_time_days IS 'Срок поставки (дни)';

CREATE INDEX IF NOT EXISTS idx_vendor_type          ON iktk.vendor_catalog(equipment_type);
CREATE INDEX IF NOT EXISTS idx_vendor_manufacturer  ON iktk.vendor_catalog(manufacturer);
CREATE INDEX IF NOT EXISTS idx_vendor_available     ON iktk.vendor_catalog(available) WHERE available = TRUE;
CREATE INDEX IF NOT EXISTS idx_vendor_cost          ON iktk.vendor_catalog(cost);
CREATE INDEX IF NOT EXISTS idx_vendor_lead_time     ON iktk.vendor_catalog(lead_time_days);
CREATE INDEX IF NOT EXISTS idx_vendor_external      ON iktk.vendor_catalog(external_id);
CREATE INDEX IF NOT EXISTS idx_vendor_cnc           ON iktk.vendor_catalog(cnc_capable) WHERE cnc_capable = TRUE;
CREATE INDEX IF NOT EXISTS idx_vendor_name_trgm     ON iktk.vendor_catalog USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_vendor_created_at    ON iktk.vendor_catalog(created_at DESC);

CREATE TRIGGER trg_vendor_updated_at
    BEFORE UPDATE ON iktk.vendor_catalog
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 3. Рекомендации по оборудованию
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.equipment_recommendations (
    id                UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    project_id        UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    gap_id            UUID,
    analysis_id       UUID,
    type              iktk.recommendation_type NOT NULL,
    status            iktk.recommendation_status NOT NULL DEFAULT 'PENDING',
    equipment_id      UUID REFERENCES iktk.equipment(id) ON DELETE SET NULL,
    vendor_id         UUID REFERENCES iktk.vendor_catalog(id) ON DELETE SET NULL,
    rationale         VARCHAR(2000),
    score             NUMERIC(8,4) CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    estimated_cost    NUMERIC(14,2),
    lead_time_days    INT,
    accepted_by       VARCHAR(128),
    accepted_at       TIMESTAMPTZ,
    parameters        JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.equipment_recommendations IS 'Рекомендации по закрытию дефицита оборудования';
COMMENT ON COLUMN iktk.equipment_recommendations.type IS 'ALTERNATIVE, PURCHASE, RENT, OUTSOURCE, ADJUST_PROCESS';
COMMENT ON COLUMN iktk.equipment_recommendations.score IS 'Интегральный скор (0…1)';
COMMENT ON COLUMN iktk.equipment_recommendations.accepted_by IS 'Кто принял рекомендацию';

CREATE INDEX IF NOT EXISTS idx_rec_project       ON iktk.equipment_recommendations(project_id);
CREATE INDEX IF NOT EXISTS idx_rec_gap           ON iktk.equipment_recommendations(gap_id);
CREATE INDEX IF NOT EXISTS idx_rec_analysis      ON iktk.equipment_recommendations(analysis_id);
CREATE INDEX IF NOT EXISTS idx_rec_type          ON iktk.equipment_recommendations(type);
CREATE INDEX IF NOT EXISTS idx_rec_status        ON iktk.equipment_recommendations(status);
CREATE INDEX IF NOT EXISTS idx_rec_equipment     ON iktk.equipment_recommendations(equipment_id);
CREATE INDEX IF NOT EXISTS idx_rec_vendor        ON iktk.equipment_recommendations(vendor_id);
CREATE INDEX IF NOT EXISTS idx_rec_score         ON iktk.equipment_recommendations(score DESC);
CREATE INDEX IF NOT EXISTS idx_rec_project_score ON iktk.equipment_recommendations(project_id, score DESC);
CREATE INDEX IF NOT EXISTS idx_rec_gap_score     ON iktk.equipment_recommendations(gap_id, score DESC);
CREATE INDEX IF NOT EXISTS idx_rec_created_at    ON iktk.equipment_recommendations(created_at DESC);

CREATE TRIGGER trg_rec_updated_at
    BEFORE UPDATE ON iktk.equipment_recommendations
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 4. Варианты аутсорса
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS iktk.outsourcing_options (
    id                    UUID PRIMARY KEY DEFAULT iktk.new_uuid(),
    gap_id                UUID NOT NULL,
    project_id            UUID NOT NULL REFERENCES iktk.projects(id) ON DELETE CASCADE,
    analysis_id           UUID,
    type                  iktk.outsourcing_type NOT NULL DEFAULT 'CONTRACTOR',
    contractor            VARCHAR(255),
    contractor_contact    VARCHAR(255),
    contractor_region     VARCHAR(64),
    operation             VARCHAR(128),
    cost                  NUMERIC(14,2),
    lead_time_days        INT,
    quality_rating        NUMERIC(8,4) CHECK (quality_rating IS NULL OR (quality_rating >= 0 AND quality_rating <= 1)),
    score                 NUMERIC(8,4) CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    status                iktk.outsourcing_status NOT NULL DEFAULT 'PENDING',
    comment               VARCHAR(1000),
    selected_by           VARCHAR(128),
    selected_at           TIMESTAMPTZ,
    parameters            JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  iktk.outsourcing_options IS 'Варианты аутсорса для дефицита оборудования';
COMMENT ON COLUMN iktk.outsourcing_options.type IS 'CONTRACTOR, PARTNER_PLANT, RENTAL, COOPERATION';
COMMENT ON COLUMN iktk.outsourcing_options.contractor IS 'Наименование подрядчика';
COMMENT ON COLUMN iktk.outsourcing_options.quality_rating IS 'Рейтинг качества (0…1)';
COMMENT ON COLUMN iktk.outsourcing_options.score IS 'Интегральный скор (0…1)';

CREATE INDEX IF NOT EXISTS idx_out_gap           ON iktk.outsourcing_options(gap_id);
CREATE INDEX IF NOT EXISTS idx_out_project       ON iktk.outsourcing_options(project_id);
CREATE INDEX IF NOT EXISTS idx_out_analysis      ON iktk.outsourcing_options(analysis_id);
CREATE INDEX IF NOT EXISTS idx_out_type          ON iktk.outsourcing_options(type);
CREATE INDEX IF NOT EXISTS idx_out_status        ON iktk.outsourcing_options(status);
CREATE INDEX IF NOT EXISTS idx_out_contractor    ON iktk.outsourcing_options(contractor);
CREATE INDEX IF NOT EXISTS idx_out_score         ON iktk.outsourcing_options(score DESC);
CREATE INDEX IF NOT EXISTS idx_out_gap_score     ON iktk.outsourcing_options(gap_id, score DESC);
CREATE INDEX IF NOT EXISTS idx_out_created_at    ON iktk.outsourcing_options(created_at DESC);

CREATE TRIGGER trg_out_updated_at
    BEFORE UPDATE ON iktk.outsourcing_options
    FOR EACH ROW EXECUTE FUNCTION iktk.set_updated_at();

-- -----------------------------------------------------------------------------
-- 5. Дополнение V4: FK equipment_requirements.analysis_id
-- -----------------------------------------------------------------------------

-- Добавляем FK на equipment_gap_analysis, если он ещё не задан.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_req_analysis'
    ) THEN
        ALTER TABLE iktk.equipment_requirements
            ADD CONSTRAINT fk_req_analysis
            FOREIGN KEY (analysis_id)
            REFERENCES iktk.equipment_gap_analysis(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_rec_gap'
    ) THEN
        ALTER TABLE iktk.equipment_recommendations
            ADD CONSTRAINT fk_rec_gap
            FOREIGN KEY (gap_id)
            REFERENCES iktk.equipment_gap_analysis(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_rec_analysis'
    ) THEN
        ALTER TABLE iktk.equipment_recommendations
            ADD CONSTRAINT fk_rec_analysis
            FOREIGN KEY (analysis_id)
            REFERENCES iktk.equipment_gap_analysis(id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_out_gap'
    ) THEN
        ALTER TABLE iktk.outsourcing_options
            ADD CONSTRAINT fk_out_gap
            FOREIGN KEY (gap_id)
            REFERENCES iktk.equipment_gap_analysis(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'fk_out_analysis'
    ) THEN
        ALTER TABLE iktk.outsourcing_options
            ADD CONSTRAINT fk_out_analysis
            FOREIGN KEY (analysis_id)
            REFERENCES iktk.equipment_gap_analysis(id)
            ON DELETE SET NULL;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 6. Представления
-- -----------------------------------------------------------------------------

-- 6.1. Рекомендации с обогащением
CREATE OR REPLACE VIEW iktk.v_recommendations_full AS
SELECT
    r.id,
    r.project_id,
    p.designation AS project_designation,
    r.gap_id,
    r.analysis_id,
    r.type,
    r.status,
    r.equipment_id,
    e.name AS equipment_name,
    r.vendor_id,
    v.name AS vendor_name,
    r.rationale,
    r.score,
    r.estimated_cost,
    r.lead_time_days,
    r.accepted_by,
    r.accepted_at,
    r.created_at
FROM iktk.equipment_recommendations r
JOIN iktk.projects p ON p.id = r.project_id
LEFT JOIN iktk.equipment e ON e.id = r.equipment_id
LEFT JOIN iktk.vendor_catalog v ON v.id = r.vendor_id;

COMMENT ON VIEW iktk.v_recommendations_full IS 'Рекомендации с обогащением данными оборудования и поставщика';

-- 6.2. Варианты аутсорса с обогащением
CREATE OR REPLACE VIEW iktk.v_outsourcing_full AS
SELECT
    o.id,
    o.project_id,
    p.designation AS project_designation,
    o.gap_id,
    o.type,
    o.contractor,
    o.operation,
    o.cost,
    o.lead_time_days,
    o.quality_rating,
    o.score,
    o.status,
    o.selected_by,
    o.selected_at,
    o.created_at
FROM iktk.outsourcing_options o
JOIN iktk.projects p ON p.id = o.project_id;

COMMENT ON VIEW iktk.v_outsourcing_full IS 'Варианты аутсорса с обогащением';

-- 6.3. Сводка по рекомендациям проекта
CREATE OR REPLACE VIEW iktk.v_recommendations_summary AS
SELECT
    r.project_id,
    p.designation AS project_designation,
    COUNT(*) AS total,
    COUNT(*) FILTER (WHERE r.status = 'PENDING')  AS pending,
    COUNT(*) FILTER (WHERE r.status = 'ACCEPTED') AS accepted,
    COUNT(*) FILTER (WHERE r.status = 'REJECTED') AS rejected,
    COALESCE(SUM(r.estimated_cost) FILTER (WHERE r.status = 'ACCEPTED'), 0) AS accepted_cost
FROM iktk.equipment_recommendations r
JOIN iktk.projects p ON p.id = r.project_id
GROUP BY r.project_id, p.designation;

COMMENT ON VIEW iktk.v_recommendations_summary IS 'Сводка по рекомендациям проекта';

-- 6.4. Каталог внешнего оборудования
CREATE OR REPLACE VIEW iktk.v_vendor_catalog AS
SELECT
    v.id,
    v.name,
    v.manufacturer,
    v.model,
    v.equipment_type,
    v.country,
    v.cost,
    v.rental_cost_per_day,
    v.lead_time_days,
    v.warranty_months,
    v.vendor_name,
    v.vendor_region,
    v.available,
    v.source,
    v.last_synced_at
FROM iktk.vendor_catalog v
WHERE v.available = TRUE;

COMMENT ON VIEW iktk.v_vendor_catalog IS 'Каталог внешнего оборудования (только доступное)';

-- -----------------------------------------------------------------------------
-- 7. Триггеры версионности
-- -----------------------------------------------------------------------------

-- Версионность рекомендаций
CREATE OR REPLACE FUNCTION iktk.on_recommendation_change()
RETURNS TRIGGER AS $$
DECLARE
    v_version_number INT;
    v_version_label  VARCHAR(64);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        SELECT COALESCE(MAX(version_number), 0) + 1
          INTO v_version_number
          FROM iktk.entity_versions
         WHERE entity_type = 'RECOMMENDATION'
           AND entity_id   = NEW.id;

        v_version_label := 'v' || v_version_number;

        UPDATE iktk.entity_versions
           SET is_current = FALSE
         WHERE entity_type = 'RECOMMENDATION'
           AND entity_id   = NEW.id
           AND is_current  = TRUE;

        INSERT INTO iktk.entity_versions (
            entity_type, entity_id, project_id, version, version_number,
            state, snapshot, change_type, is_current
        ) VALUES (
            'RECOMMENDATION', NEW.id, NEW.project_id, v_version_label, v_version_number,
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
            'RECOMMENDATION', NEW.id, NEW.project_id, 'v1', 1,
            NEW.status::TEXT,
            to_jsonb(NEW),
            'CREATE',
            TRUE
        );
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iktk.on_recommendation_change() IS 'Триггер версионности рекомендаций';

CREATE TRIGGER trg_rec_versioning
    AFTER INSERT OR UPDATE ON iktk.equipment_recommendations
    FOR EACH ROW EXECUTE FUNCTION iktk.on_recommendation_change();

-- -----------------------------------------------------------------------------
-- 8. Начальные данные: пример каталога внешнего оборудования
-- -----------------------------------------------------------------------------

INSERT INTO iktk.vendor_catalog (
    name, manufacturer, model, equipment_type, country,
    cnc_capable, cost, rental_cost_per_day, lead_time_days, warranty_months,
    vendor_name, vendor_region, available, source
) VALUES
    ('Токарный станок с ЧПУ 16К20Ф3', 'Станкозавод', '16К20Ф3', 'TURNING', 'RU',
     TRUE, 4500000, 15000, 60, 24, 'ПромСтанкоСервис', 'Москва', TRUE, 'MANUAL'),
    ('Фрезерный станок с ЧПУ DMU 50', 'DMG Mori', 'DMU 50', 'MILLING', 'DE',
     TRUE, 12500000, 35000, 120, 36, 'ООО ТехноИмпорт', 'Москва', TRUE, 'MANUAL'),
    ('Гидравлический пресс 250 тс', 'БарнаулПресс', 'ПГ-250', 'PRESS', 'RU',
     FALSE, 6800000, 20000, 90, 24, 'ПромОборудование', 'Барнаул', TRUE, 'MANUAL'),
    ('Литьевая машина под давлением 300 т', 'Bühler', 'Carat 300', 'CASTING', 'CH',
     FALSE, 18500000, 55000, 180, 36, 'ООО ИндустрияЛит', 'Москва', TRUE, 'MANUAL'),
    ('Шлифовальный станок с ЧПУ Studer S33', 'Studer', 'S33', 'GRINDING', 'CH',
     TRUE, 15500000, 45000, 150, 24, 'Точные Станки', 'СПб', TRUE, 'MANUAL')
ON CONFLICT (external_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 9. Комментарий о миграции
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    RAISE NOTICE 'ИКТК: миграция V6__recommendations.sql применена успешно';
END $$;

-- =============================================================================
-- Конец миграции V6__recommendations.sql
-- =============================================================================