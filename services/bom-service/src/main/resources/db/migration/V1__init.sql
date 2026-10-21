-- V1__init.sql (bom-service)
-- Схема BOM: assembly_units, bom_items, assembly_connections.
-- id — VARCHAR(64), FK внутри схемы отсутствуют (изоляция module-БД).

CREATE TABLE IF NOT EXISTS assembly_units (
    id              VARCHAR(64) PRIMARY KEY,
    project_id      VARCHAR(64) NOT NULL,
    parent_id       VARCHAR(64),
    designation     VARCHAR(128) NOT NULL,
    name            VARCHAR(255),
    description     VARCHAR(2000),
    geometry_id     VARCHAR(64),
    cad_system      VARCHAR(32),
    item_count      INT NOT NULL DEFAULT 0,
    max_depth       INT NOT NULL DEFAULT 0,
    total_mass_kg   NUMERIC(14,4),
    version         INT NOT NULL DEFAULT 1,
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    updated_by      VARCHAR(128),
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_assembly_units_project    ON assembly_units(project_id);
CREATE INDEX IF NOT EXISTS idx_assembly_units_parent     ON assembly_units(parent_id);
CREATE INDEX IF NOT EXISTS idx_assembly_units_geometry   ON assembly_units(geometry_id);
CREATE INDEX IF NOT EXISTS idx_assembly_units_status     ON assembly_units(status);

CREATE TABLE IF NOT EXISTS bom_items (
    id              VARCHAR(64) PRIMARY KEY,
    assembly_id     VARCHAR(64) NOT NULL,
    parent_id       VARCHAR(64),
    item_order      INT NOT NULL DEFAULT 0,
    level           INT NOT NULL DEFAULT 0,
    position        VARCHAR(32),
    designation     VARCHAR(128),
    name            VARCHAR(255),
    quantity        INT NOT NULL DEFAULT 1,
    unit            VARCHAR(32) DEFAULT 'шт',
    material        VARCHAR(128),
    item_type       VARCHAR(32) NOT NULL DEFAULT 'PART',
    geometry_id     VARCHAR(64),
    cad_system      VARCHAR(32),
    mass_kg         NUMERIC(12,4),
    note            VARCHAR(1000),
    parameters      JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bom_items_assembly     ON bom_items(assembly_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_parent       ON bom_items(parent_id);
CREATE INDEX IF NOT EXISTS idx_bom_items_type         ON bom_items(item_type);
CREATE INDEX IF NOT EXISTS idx_bom_items_designation  ON bom_items(designation);
CREATE INDEX IF NOT EXISTS idx_bom_items_material     ON bom_items(material);

CREATE TABLE IF NOT EXISTS assembly_connections (
    id                  VARCHAR(64) PRIMARY KEY,
    assembly_id         VARCHAR(64) NOT NULL,
    connection_type     VARCHAR(32) NOT NULL,
    connection_name     VARCHAR(255),
    description         VARCHAR(1000),
    part1_id            VARCHAR(64) NOT NULL,
    part2_id            VARCHAR(64) NOT NULL,
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
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_assembly_conn_assembly  ON assembly_connections(assembly_id);
CREATE INDEX IF NOT EXISTS idx_assembly_conn_type      ON assembly_connections(connection_type);
CREATE INDEX IF NOT EXISTS idx_assembly_conn_part1     ON assembly_connections(part1_id);
CREATE INDEX IF NOT EXISTS idx_assembly_conn_part2     ON assembly_connections(part2_id);