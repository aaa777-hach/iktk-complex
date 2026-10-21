-- =============================================================================
-- recommendation-service: self-contained schema (public)
-- IDs: VARCHAR(64) — совпадает с моделями (@Id String, length=64)
-- Без кросс-сервисных FK: projects/equipment живут в отдельных БД
-- =============================================================================

-- 1. Триггерная функция updated_at
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 2. equipment_gap_analysis
-- =============================================================================
CREATE TABLE IF NOT EXISTS equipment_gap_analysis (
    id                     VARCHAR(64) PRIMARY KEY,
    project_id             VARCHAR(64) NOT NULL,
    strategy               VARCHAR(32) NOT NULL,
    status                 VARCHAR(32) NOT NULL,
    requested_by           VARCHAR(128),
    gaps_count             INT NOT NULL DEFAULT 0,
    recommendations_count  INT NOT NULL DEFAULT 0,
    gaps                   JSONB,
    summary                JSONB,
    started_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at           TIMESTAMPTZ,
    error                  VARCHAR(2000),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gap_project    ON equipment_gap_analysis(project_id);
CREATE INDEX IF NOT EXISTS idx_gap_strategy   ON equipment_gap_analysis(strategy);
CREATE INDEX IF NOT EXISTS idx_gap_status     ON equipment_gap_analysis(status);
CREATE INDEX IF NOT EXISTS idx_gap_started_at ON equipment_gap_analysis(started_at DESC);
DROP TRIGGER IF EXISTS trg_gap_updated_at ON equipment_gap_analysis;
CREATE TRIGGER trg_gap_updated_at BEFORE UPDATE ON equipment_gap_analysis
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 3. equipment_recommendations
-- =============================================================================
CREATE TABLE IF NOT EXISTS equipment_recommendations (
    id              VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(64) NOT NULL,
    gap_id          VARCHAR(64),
    analysis_id     VARCHAR(64),
    type            VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    equipment_id    VARCHAR(64),
    vendor_id       VARCHAR(64),
    rationale       VARCHAR(2000),
    score           NUMERIC(8,4)  CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    estimated_cost  NUMERIC(14,2),
    lead_time_days  INT,
    accepted_by     VARCHAR(128),
    accepted_at     TIMESTAMPTZ,
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rec_project    ON equipment_recommendations(project_id);
CREATE INDEX IF NOT EXISTS idx_rec_gap        ON equipment_recommendations(gap_id);
CREATE INDEX IF NOT EXISTS idx_rec_analysis   ON equipment_recommendations(analysis_id);
CREATE INDEX IF NOT EXISTS idx_rec_type       ON equipment_recommendations(type);
CREATE INDEX IF NOT EXISTS idx_rec_status     ON equipment_recommendations(status);
CREATE INDEX IF NOT EXISTS idx_rec_equipment  ON equipment_recommendations(equipment_id);
CREATE INDEX IF NOT EXISTS idx_rec_vendor     ON equipment_recommendations(vendor_id);
CREATE INDEX IF NOT EXISTS idx_rec_score      ON equipment_recommendations(score DESC);
CREATE INDEX IF NOT EXISTS idx_rec_created_at ON equipment_recommendations(created_at DESC);
DROP TRIGGER IF EXISTS trg_rec_updated_at ON equipment_recommendations;
CREATE TRIGGER trg_rec_updated_at BEFORE UPDATE ON equipment_recommendations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 4. equipment_requirements
-- =============================================================================
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
    priority                 INT NOT NULL DEFAULT 0,
    notes                    VARCHAR(1000),
    parameters               JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_req_project        ON equipment_requirements(project_id);
CREATE INDEX IF NOT EXISTS idx_req_process        ON equipment_requirements(process_id);
CREATE INDEX IF NOT EXISTS idx_req_stage          ON equipment_requirements(stage_id);
CREATE INDEX IF NOT EXISTS idx_req_operation      ON equipment_requirements(operation_id);
CREATE INDEX IF NOT EXISTS idx_req_analysis       ON equipment_requirements(analysis_id);
CREATE INDEX IF NOT EXISTS idx_req_operation_type ON equipment_requirements(operation_type);
CREATE INDEX IF NOT EXISTS idx_req_equipment_type ON equipment_requirements(required_equipment_type);
CREATE INDEX IF NOT EXISTS idx_req_material       ON equipment_requirements(material);
CREATE INDEX IF NOT EXISTS idx_req_cnc            ON equipment_requirements(required_cnc);
CREATE INDEX IF NOT EXISTS idx_req_priority       ON equipment_requirements(priority);
CREATE INDEX IF NOT EXISTS idx_req_created_at     ON equipment_requirements(created_at);
DROP TRIGGER IF EXISTS trg_req_updated_at ON equipment_requirements;
CREATE TRIGGER trg_req_updated_at BEFORE UPDATE ON equipment_requirements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 5. vendor_catalog
-- =============================================================================
CREATE TABLE IF NOT EXISTS vendor_catalog (
    id                VARCHAR(64) PRIMARY KEY,
    vendor_id         VARCHAR(64),
    vendor_name       VARCHAR(256),
    equipment_type    VARCHAR(128),
    equipment_model   VARCHAR(128),
    price             NUMERIC(14,2),
    lead_time_days    INT,
    contact_info      VARCHAR(512),
    region            VARCHAR(128),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    specifications    JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_vendor_type      ON vendor_catalog(equipment_type);
CREATE INDEX IF NOT EXISTS idx_vendor_region    ON vendor_catalog(region);
CREATE INDEX IF NOT EXISTS idx_vendor_active    ON vendor_catalog(active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_vendor_cost      ON vendor_catalog(price);
DROP TRIGGER IF EXISTS trg_vendor_updated_at ON vendor_catalog;
CREATE TRIGGER trg_vendor_updated_at BEFORE UPDATE ON vendor_catalog
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 6. outsourcing_options
-- =============================================================================
CREATE TABLE IF NOT EXISTS outsourcing_options (
    id              VARCHAR(64) PRIMARY KEY,
    gap_id          VARCHAR(64),
    project_id      VARCHAR(64),
    type            VARCHAR(32),
    operation       VARCHAR(128),
    contractor      VARCHAR(256),
    cost            NUMERIC(14,2),
    quality_rating  NUMERIC(8,4) CHECK (quality_rating IS NULL OR (quality_rating >= 0 AND quality_rating <= 1)),
    comment         VARCHAR(2000),
    lead_time_days  INT,
    status          VARCHAR(32),
    score           NUMERIC(8,4) CHECK (score IS NULL OR (score >= 0 AND score <= 1)),
    selected_by     VARCHAR(128),
    selected_at     TIMESTAMPTZ,
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_outs_gap        ON outsourcing_options(gap_id);
CREATE INDEX IF NOT EXISTS idx_outs_project    ON outsourcing_options(project_id);
CREATE INDEX IF NOT EXISTS idx_outs_type       ON outsourcing_options(type);
CREATE INDEX IF NOT EXISTS idx_outs_status     ON outsourcing_options(status);
CREATE INDEX IF NOT EXISTS idx_outs_contractor ON outsourcing_options(contractor);
CREATE INDEX IF NOT EXISTS idx_outs_score      ON outsourcing_options(score DESC);
DROP TRIGGER IF EXISTS trg_outs_updated_at ON outsourcing_options;
CREATE TRIGGER trg_outs_updated_at BEFORE UPDATE ON outsourcing_options
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =============================================================================
-- 7. Локальные FK (только внутри этой БД)
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rec_gap_local') THEN
        ALTER TABLE equipment_recommendations
            ADD CONSTRAINT fk_rec_gap_local FOREIGN KEY (gap_id)
            REFERENCES equipment_gap_analysis(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_rec_analysis_local') THEN
        ALTER TABLE equipment_recommendations
            ADD CONSTRAINT fk_rec_analysis_local FOREIGN KEY (analysis_id)
            REFERENCES equipment_gap_analysis(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_out_gap_local') THEN
        ALTER TABLE outsourcing_options
            ADD CONSTRAINT fk_out_gap_local FOREIGN KEY (gap_id)
            REFERENCES equipment_gap_analysis(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_req_analysis_local') THEN
        ALTER TABLE equipment_requirements
            ADD CONSTRAINT fk_req_analysis_local FOREIGN KEY (analysis_id)
            REFERENCES equipment_gap_analysis(id) ON DELETE SET NULL;
    END IF;
END $$;