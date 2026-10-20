package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "equipment_status",
        indexes = {
                @Index(name = "idx_status_equipment",  columnList = "equipment_id", unique = true),
                @Index(name = "idx_status_state",      columnList = "state"),
                @Index(name = "idx_status_load",       columnList = "load_percent"),
                @Index(name = "idx_status_next_maint", columnList = "next_maintenance_at")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentStatus {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank
    @Size(max = 64)
    @Column(name = "equipment_id", nullable = false, length = 64, unique = true)
    private String equipmentId;

    @NotBlank
    @Size(max = 32)
    @Column(name = "state", nullable = false, length = 32)
    private String state;

    @Min(0) @Max(100)
    @Column(name = "load_percent", nullable = false)
    private Integer loadPercent;

    @Size(max = 64)
    @Column(name = "schedule", length = 64)
    private String schedule;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "next_maintenance_at")
    private Instant nextMaintenanceAt;

    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EquipmentStatus() {}

    public EquipmentStatus(String id, String equipmentId) {
        this.id = id;
        this.equipmentId = equipmentId;
        this.state = "AVAILABLE";
        this.loadPercent = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; touch(); }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; touch(); }
    public Integer getLoadPercent() { return loadPercent; }
    public void setLoadPercent(Integer loadPercent) { this.loadPercent = loadPercent; touch(); }
    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; touch(); }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; touch(); }
    public Instant getNextMaintenanceAt() { return nextMaintenanceAt; }
    public void setNextMaintenanceAt(Instant nextMaintenanceAt) { this.nextMaintenanceAt = nextMaintenanceAt; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentStatus other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "EquipmentStatus{id='" + id + "', equipmentId='" + equipmentId
                + "', state='" + state + "', loadPercent=" + loadPercent + '}';
    }
}