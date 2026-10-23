package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Доменная модель совместимости единицы оборудования с оснасткой.
 *
 * <p>Описывает связь «оборудование — оснастка» через составной ключ
 * {@link EquipmentToolingId}:
 * <ul>
 *   <li>идентификация: {@code id} ({@code equipmentId}, {@code toolingId});</li>
 *   <li>степень совместимости: {@code compatibility} — FULL, PARTIAL,
 *       NONE;</li>
 *   <li>примечания: {@code notes}.</li>
 * </ul>
 *
 * <p><b>Использование:</b> проверка применимости оснастки при выборе ТП
 * и matching.
 *
 * <p>Таблица: {@code equipment_tooling} (PostgreSQL, миграция V3).
 */
@Entity
@Table(
        name = "equipment_tooling",
        indexes = {
                @Index(name = "idx_etooling_tooling", columnList = "tooling_id")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentTooling {

    @EmbeddedId
    private EquipmentToolingId id;

    @NotBlank
    @Size(max = 32)
    @Column(name = "compatibility", nullable = false, length = 32)
    private String compatibility;

    @Size(max = 512)
    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EquipmentTooling() {}

    public EquipmentTooling(String equipmentId, String toolingId) {
        this.id = new EquipmentToolingId(equipmentId, toolingId);
        this.compatibility = "FULL";
        this.createdAt = Instant.now();
    }

    public EquipmentToolingId getId() { return id; }
    public void setId(EquipmentToolingId id) { this.id = id; }
    public String getCompatibility() { return compatibility; }
    public void setCompatibility(String compatibility) { this.compatibility = compatibility; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "EquipmentTooling{" + id + ", compatibility='" + compatibility + "'}";
    }
}
