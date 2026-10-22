package ru.iktk.equipment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "equipment_assignments",
        indexes = {
                @Index(name = "idx_assign_project",         columnList = "project_id"),
                @Index(name = "idx_assign_equipment",       columnList = "equipment_id"),
                @Index(name = "idx_assign_active",          columnList = "active"),
                @Index(name = "idx_assign_project_equipment", columnList = "project_id, equipment_id")
        }
)
public class EquipmentAssignment {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "equipment_id", nullable = false, length = 64)
    private String equipmentId;

    @Column(name = "assigned_by", length = 128)
    private String assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected EquipmentAssignment() {
    }

    public EquipmentAssignment(String id, String projectId, String equipmentId, String assignedBy) {
        this.id = id;
        this.projectId = projectId;
        this.equipmentId = equipmentId;
        this.assignedBy = assignedBy;
        this.assignedAt = Instant.now();
        this.active = true;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getEquipmentId() { return equipmentId; }
    public String getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getUnassignedAt() { return unassignedAt; }
    public boolean isActive() { return active; }

    public void unassign() {
        this.active = false;
        this.unassignedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentAssignment other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}