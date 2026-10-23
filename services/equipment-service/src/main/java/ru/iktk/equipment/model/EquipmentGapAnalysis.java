package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная модель gap-анализа ИКТК.
 *
 * <p>Описывает результат сопоставления требований к оборудованию
 * (из спроектированных ТП) с фактическим парком предприятия:
 * <ul>
 *   <li>идентификация: {@code projectId};</li>
 *   <li>стратегия сопоставления: {@code strategy} — STRICT, FLEXIBLE,
 *       HYBRID;</li>
 *   <li>статус: {@code status} — RUNNING, COMPLETED, FAILED;</li>
 *   <li>инициатор: {@code requestedBy};</li>
 *   <li>результаты: {@code gapsCount}, {@code recommendationsCount},
 *       {@code gaps} (JSONB), {@code summary} (JSONB);</li>
 *   <li>тайминги: {@code startedAt}, {@code completedAt}.</li>
 * </ul>
 *
 * <p><b>Жизненный цикл:</b>
 * <ol>
 *   <li>{@code RUNNING} — анализ запущен;</li>
 *   <li>{@code COMPLETED} — анализ завершён, дефициты зафиксированы;</li>
 *   <li>{@code FAILED} — ошибка, {@code error} заполнен.</li>
 * </ol>
 *
 * <p>Таблица: {@code equipment_gap_analysis} (PostgreSQL, миграция V3).
 */
@Entity
@Table(
        name = "equipment_gap_analysis",
        indexes = {
                @Index(name = "idx_gap_project",    columnList = "project_id, started_at"),
                @Index(name = "idx_gap_strategy",   columnList = "strategy"),
                @Index(name = "idx_gap_status",     columnList = "status"),
                @Index(name = "idx_gap_started_at", columnList = "started_at")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentGapAnalysis {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @NotBlank @Size(max = 32)
    @Column(name = "strategy", nullable = false, length = 32)
    private String strategy;

    @NotBlank @Size(max = 32)
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Size(max = 128)
    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    @Column(name = "gaps_count", nullable = false)            private Integer gapsCount = 0;
    @Column(name = "recommendations_count", nullable = false) private Integer recommendationsCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> gaps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "completed_at")                                    private Instant completedAt;

    @Size(max = 2000)
    @Column(name = "error", length = 2000) private String error;

    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EquipmentGapAnalysis() {}

    public EquipmentGapAnalysis(String id, String projectId) {
        this.id = id;
        this.projectId = projectId;
        this.strategy = "STRICT";
        this.status = "RUNNING";
        this.gaps = List.of();
        Instant now = Instant.now();
        this.startedAt = now;
        this.updatedAt = now;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; touch(); }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; touch(); }
    public Integer getGapsCount() { return gapsCount; }
    public void setGapsCount(Integer gapsCount) { this.gapsCount = gapsCount; touch(); }
    public Integer getRecommendationsCount() { return recommendationsCount; }
    public void setRecommendationsCount(Integer v) { this.recommendationsCount = v; touch(); }
    public List<Map<String, Object>> getGaps() { return gaps; }
    public void setGaps(List<Map<String, Object>> gaps) { this.gaps = gaps; touch(); }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; touch(); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; touch(); }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; touch(); }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentGapAnalysis other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "EquipmentGapAnalysis{id='" + id + "', projectId='" + projectId
                + "', strategy='" + strategy + "', status='" + status
                + "', gapsCount=" + gapsCount + '}';
    }
}
