-- =============================================================================
-- equipment-service: назначения оборудования на проекты
-- Миграция V4: equipment_assignments
-- =============================================================================

CREATE TABLE IF NOT EXISTS equipment_assignments (
    id             VARCHAR(64) PRIMARY KEY,
    project_id     VARCHAR(64) NOT NULL,
    equipment_id   VARCHAR(64) NOT NULL,
    assigned_by    VARCHAR(128),
    assigned_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    unassigned_at  TIMESTAMPTZ,
    active         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_assign_project   ON equipment_assignments(project_id);
CREATE INDEX IF NOT EXISTS idx_assign_equipment ON equipment_assignments(equipment_id);
CREATE INDEX IF NOT EXISTS idx_assign_active    ON equipment_assignments(active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_assign_project_equipment ON equipment_assignments(project_id, equipment_id);