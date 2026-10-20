package ru.iktk.equipment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EquipmentToolingId implements Serializable {

    @Column(name = "equipment_id", nullable = false, length = 64)
    private String equipmentId;

    @Column(name = "tooling_id", nullable = false, length = 64)
    private String toolingId;

    public EquipmentToolingId() {}

    public EquipmentToolingId(String equipmentId, String toolingId) {
        this.equipmentId = equipmentId;
        this.toolingId = toolingId;
    }

    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }
    public String getToolingId() { return toolingId; }
    public void setToolingId(String toolingId) { this.toolingId = toolingId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentToolingId other)) return false;
        return Objects.equals(equipmentId, other.equipmentId)
                && Objects.equals(toolingId, other.toolingId);
    }

    @Override
    public int hashCode() { return Objects.hash(equipmentId, toolingId); }

    @Override
    public String toString() {
        return "EquipmentToolingId{equipmentId='" + equipmentId + "', toolingId='" + toolingId + "'}";
    }
}