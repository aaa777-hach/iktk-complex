-- V1__init.sql (iktk_equipment) — минимальная модель для isMethodAvailable
-- По entity: Equipment.java, EquipmentCapability.java (из .bak)
-- Не включает: equipment_status (enum), equipment_tooling, equipment_requirements,
-- equipment_gap_analysis — будущие этапы (V2+)

CREATE TABLE IF NOT EXISTS equipment (
    id                  VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    equipment_type      VARCHAR(64)  NOT NULL,
    model               VARCHAR(128),
    manufacturer        VARCHAR(128),
    inventory_no        VARCHAR(64) UNIQUE,
    serial_no           VARCHAR(64),
    manufacture_year    INT,
    enterprise_code     VARCHAR(64),
    department_code     VARCHAR(64),
    location            VARCHAR(255),
    status              VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    responsible         VARCHAR(128),
    commissioned_at     TIMESTAMPTZ,
    decommissioned_at   TIMESTAMPTZ,
    parameters          JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_equipment_type          ON equipment(equipment_type);
CREATE INDEX IF NOT EXISTS idx_equipment_status        ON equipment(status);
CREATE INDEX IF NOT EXISTS idx_equipment_inventory_no  ON equipment(inventory_no);
CREATE INDEX IF NOT EXISTS idx_equipment_enterprise    ON equipment(enterprise_code);
CREATE INDEX IF NOT EXISTS idx_equipment_department    ON equipment(department_code);
CREATE INDEX IF NOT EXISTS idx_equipment_location      ON equipment(location);
CREATE INDEX IF NOT EXISTS idx_equipment_created_at    ON equipment(created_at DESC);

CREATE TABLE IF NOT EXISTS equipment_capabilities (
    id                    VARCHAR(64) PRIMARY KEY,
    equipment_id          VARCHAR(64) NOT NULL,
    operation_type        VARCHAR(64) NOT NULL,
    operation_name        VARCHAR(255),
    operation_category    VARCHAR(32),
    tolerance             VARCHAR(32),
    accuracy_grade        INT,
    max_dimensions        VARCHAR(64),
    min_dimensions        VARCHAR(64),
    max_diameter_mm       NUMERIC(12,3),
    max_length_mm         NUMERIC(12,3),
    max_mass_kg           NUMERIC(12,3),
    power_kw              NUMERIC(10,3),
    force_kn              NUMERIC(12,3),
    temperature_c         NUMERIC(8,2),
    spindle_speed_min_rpm INT,
    spindle_speed_max_rpm INT,
    feed_min              NUMERIC(10,3),
    feed_max              NUMERIC(10,3),
    productivity_per_hour NUMERIC(10,2),
    cnc_capable           BOOLEAN NOT NULL DEFAULT FALSE,
    axes_count            INT,
    cnc_system            VARCHAR(64),
    parameters            JSONB,
    comment               VARCHAR(1000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_capability_equipment_operation UNIQUE (equipment_id, operation_type),
    CONSTRAINT fk_cap_equipment FOREIGN KEY (equipment_id)
        REFERENCES equipment(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_cap_equipment ON equipment_capabilities(equipment_id);
CREATE INDEX IF NOT EXISTS idx_cap_operation ON equipment_capabilities(operation_type);
CREATE INDEX IF NOT EXISTS idx_cap_cnc ON equipment_capabilities(cnc_capable) WHERE cnc_capable = TRUE;
CREATE INDEX IF NOT EXISTS idx_cap_accuracy ON equipment_capabilities(accuracy_grade);

CREATE TABLE IF NOT EXISTS equipment_capability_materials (
    capability_id   VARCHAR(64) NOT NULL,
    material_code   VARCHAR(64) NOT NULL,
    PRIMARY KEY (capability_id, material_code),
    CONSTRAINT fk_cap_materials FOREIGN KEY (capability_id)
        REFERENCES equipment_capabilities(id) ON DELETE CASCADE
);