-- V1__init.sql (iktk_geometry) — геометрия для casting/integration
-- Поля соответствуют CastingProcessService.GeometryData (@JsonCreator)
--       + id (PK), project_id (опц.), created_at/updated_at

CREATE TABLE IF NOT EXISTS geometry (
    id                   VARCHAR(64) PRIMARY KEY,
    object_ref           VARCHAR(64) NOT NULL UNIQUE,
    project_id           VARCHAR(64),
    material             VARCHAR(100) NOT NULL,
    volume               NUMERIC(18,4) NOT NULL,
    mass                 NUMERIC(18,4) NOT NULL,
    min_wall_thickness   NUMERIC(10,4) NOT NULL,
    min_fillet_radius    NUMERIC(10,4) NOT NULL,
    min_draft_angle      NUMERIC(10,4) NOT NULL,
    nominal_dimensions   NUMERIC(12,4) NOT NULL,
    complexity_score     INTEGER NOT NULL,
    undercuts            BOOLEAN NOT NULL DEFAULT FALSE,
    hot_spots            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_geometry_object_ref ON geometry(object_ref);
CREATE INDEX IF NOT EXISTS idx_geometry_project    ON geometry(project_id);
CREATE INDEX IF NOT EXISTS idx_geometry_material   ON geometry(material);