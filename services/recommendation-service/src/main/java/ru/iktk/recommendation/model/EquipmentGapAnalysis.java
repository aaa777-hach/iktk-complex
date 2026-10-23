package ru.iktk.recommendation.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Доменная модель gap-анализа ИКТК.
 *
 * <p>Описывает результат сопоставления требований к оборудованию
 * (из спроектированных ТП) с фактическим парком предприятия.
 * Хранит список выявленных дефицитов ({@link Gap}) и агрегированные
 * показатели.
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code projectId} — проект;</li>
 *   <li>{@code strategy} — стратегия сопоставления
 *       (STRICT, FLEXIBLE, HYBRID);</li>
 *   <li>{@code status} — RUNNING, COMPLETED, FAILED;</li>
 *   <li>{@code gapsCount} — число выявленных дефицитов;</li>
 *   <li>{@code recommendationsCount} — число сформированных
 *       рекомендаций;</li>
 *   <li>{@code gaps} — список дефицитов ({@link Gap}, JSONB);</li>
 *   <li>{@code startedAt}, {@code completedAt} — временные метки;</li>
 *   <li>{@code error} — сообщение об ошибке (при FAILED).</li>
 * </ul>
 *
 * <p><b>Жизненный цикл:</b>
 * <ol>
 *   <li>{@code RUNNING} — анализ запущен (создание записи);</li>
 *   <li>{@code COMPLETED} — анализ завершён, дефициты и рекомендации
 *       зафиксированы;</li>
 *   <li>{@code FAILED} — ошибка, {@code error} заполнен.</li>
 * </ol>
 *
 * <p>Таблица: {@code equipment_gap_analysis} (PostgreSQL, миграция V6).
 */
@Entity
@Table(
        name = "equipment_gap_analysis",
        indexes = {
                @Index(name = "idx_gap_project",    columnList = "project_id"),
                @Index(name = "idx_gap_strategy",   columnList = "strategy"),
                @Index(name = "idx_gap_status",     columnList = "status"),
                @Index(name = "idx_gap_started_at", columnList = "started_at DESC")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentGapAnalysis {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор проекта. */
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    // ------------------------------------------------------------------
    // Параметры анализа
    // ------------------------------------------------------------------

    /** Стратегия сопоставления: STRICT, FLEXIBLE, HYBRID. */
    @NotBlank
    @Size(max = 32)
    @Column(name = "strategy", nullable = false, length = 32)
    private String strategy;

    /** Статус: RUNNING, COMPLETED, FAILED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** Кто запустил анализ. */
    @Size(max = 128)
    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    // ------------------------------------------------------------------
    // Результаты
    // ------------------------------------------------------------------

    /** Число выявленных дефицитов. */
    @Column(name = "gaps_count", nullable = false)
    private int gapsCount = 0;

    /** Число сформированных рекомендаций. */
    @Column(name = "recommendations_count", nullable = false)
    private int recommendationsCount = 0;

    /**
     * Список дефицитов ({@link Gap}).
     * Хранится как JSONB — для гибкости и скорости.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps", columnDefinition = "jsonb")
    private List<Gap> gaps = new ArrayList<>();

    /** Сводные метрики (JSONB): bySeverity, byOperationType и т.п. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary;

    // ------------------------------------------------------------------
    // Служебные поля
    // ------------------------------------------------------------------

    /** Момент запуска. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Момент завершения. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Сообщение об ошибке (при FAILED). */
    @Size(max = 2000)
    @Column(name = "error", length = 2000)
    private String error;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    protected EquipmentGapAnalysis() {
    }

    public EquipmentGapAnalysis(String id, String projectId, String strategy) {
        this.id = id;
        this.projectId = projectId;
        this.strategy = strategy;
        this.status = GapStatus.RUNNING.name();
        Instant now = Instant.now();
        this.startedAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String projectId;
        private String strategy;
        private String status;
        private String requestedBy;
        private int gapsCount;
        private int recommendationsCount;
        private List<Gap> gaps = new ArrayList<>();
        private Map<String, Object> summary;
        private Instant startedAt;
        private Instant completedAt;
        private String error;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder strategy(String v) { this.strategy = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder requestedBy(String v) { this.requestedBy = v; return this; }
        public Builder gapsCount(int v) { this.gapsCount = v; return this; }
        public Builder recommendationsCount(int v) { this.recommendationsCount = v; return this; }
        public Builder gaps(List<Gap> v) { this.gaps = v; return this; }
        public Builder summary(Map<String, Object> v) { this.summary = v; return this; }
        public Builder startedAt(Instant v) { this.startedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder error(String v) { this.error = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public EquipmentGapAnalysis build() {
            EquipmentGapAnalysis a = new EquipmentGapAnalysis();
            a.id = this.id;
            a.projectId = this.projectId;
            a.strategy = this.strategy;
            a.status = this.status == null ? GapStatus.RUNNING.name() : this.status;
            a.requestedBy = this.requestedBy;
            a.gapsCount = this.gapsCount;
            a.recommendationsCount = this.recommendationsCount;
            a.gaps = this.gaps == null ? new ArrayList<>() : this.gaps;
            a.summary = this.summary;
            Instant now = Instant.now();
            a.startedAt = this.startedAt == null ? now : this.startedAt;
            a.completedAt = this.completedAt;
            a.error = this.error;
            a.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return a;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /** Пометить анализ как выполняющийся. */
    public void markRunning() {
        this.status = GapStatus.RUNNING.name();
        touch();
    }

    /** Пометить анализ как успешно завершённый. */
    public void markCompleted(int gapsCount, int recommendationsCount) {
        this.status = GapStatus.COMPLETED.name();
        this.gapsCount = gapsCount;
        this.recommendationsCount = recommendationsCount;
        this.completedAt = Instant.now();
        touch();
    }

    /** Пометить анализ как упавший. */
    public void markFailed(String error) {
        this.status = GapStatus.FAILED.name();
        this.error = error;
        this.completedAt = Instant.now();
        touch();
    }

    /** Завершён ли анализ. */
    public boolean isCompleted() {
        return GapStatus.COMPLETED.name().equals(this.status);
    }

    /** Провален ли анализ. */
    public boolean isFailed() {
        return GapStatus.FAILED.name().equals(this.status);
    }

    /** Выполняется ли анализ. */
    public boolean isRunning() {
        return GapStatus.RUNNING.name().equals(this.status);
    }

    /** Есть ли дефициты. */
    public boolean hasGaps() {
        return gapsCount > 0;
    }

    /** Длительность анализа в миллисекундах. */
    public long durationMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /** Добавить дефицит. */
    public void addGap(Gap gap) {
        if (gap == null) {
            return;
        }
        if (this.gaps == null) {
            this.gaps = new ArrayList<>();
        }
        this.gaps.add(gap);
        this.gapsCount = this.gaps.size();
        touch();
    }

    /** Обновить метку времени. */
    public String getOperationType() {
        if (gaps == null || gaps.isEmpty()) return null;
        return gaps.get(0).getOperationType();
    }

    public java.util.List<java.util.Map<String, Object>> getRequirements() {
        if (gaps == null || gaps.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>(gaps.size());
        for (Gap g : gaps) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("operationType", g.getOperationType());
            m.put("operationId", g.getOperationId());
            m.put("requiredAccuracy", g.getRequiredAccuracy());
            m.put("maxDimensions", g.getMaxDimensions());
            m.put("material", g.getMaterial());
            m.put("requiredPowerKw", g.getRequiredPowerKw());
            m.put("requiredForceKn", g.getRequiredForceKn());
            m.put("requiredCnc", g.isRequiredCnc());
            m.put("severity", g.getSeverity());
            result.add(m);
        }
        return result;
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

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

    public int getGapsCount() { return gapsCount; }
    public void setGapsCount(int gapsCount) { this.gapsCount = gapsCount; touch(); }

    public int getRecommendationsCount() { return recommendationsCount; }
    public void setRecommendationsCount(int recommendationsCount) { this.recommendationsCount = recommendationsCount; touch(); }

    public List<Gap> getGaps() { return gaps; }
    public void setGaps(List<Gap> gaps) { this.gaps = gaps; touch(); }

    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; touch(); }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; touch(); }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; touch(); }

    public Instant getUpdatedAt() { return updatedAt; }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EquipmentGapAnalysis other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EquipmentGapAnalysis{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", strategy='" + strategy + '\'' +
                ", status='" + status + '\'' +
                ", gapsCount=" + gapsCount +
                ", recommendationsCount=" + recommendationsCount +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенные типы: Gap и GapStatus
    // ------------------------------------------------------------------

    /**
     * Дефицит оборудования — результат сопоставления одного требования
     * с парком.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Gap {

        private String id;
        private String analysisId;
        private String projectId;
        private String processId;
        private String stageId;
        private String operationId;
        private String operationType;
        private String requiredAccuracy;
        private String maxDimensions;
        private String material;
        private BigDecimal requiredPowerKw;
        private BigDecimal requiredForceKn;
        private boolean requiredCnc;
        private String gapReason;
        private String severity;
        private String strategy;
        private Instant createdAt;

        public Gap() {
        }

        private Gap(Builder b) {
            this.id = b.id;
            this.analysisId = b.analysisId;
            this.projectId = b.projectId;
            this.processId = b.processId;
            this.stageId = b.stageId;
            this.operationId = b.operationId;
            this.operationType = b.operationType;
            this.requiredAccuracy = b.requiredAccuracy;
            this.maxDimensions = b.maxDimensions;
            this.material = b.material;
            this.requiredPowerKw = b.requiredPowerKw;
            this.requiredForceKn = b.requiredForceKn;
            this.requiredCnc = b.requiredCnc;
            this.gapReason = b.gapReason;
            this.severity = b.severity;
            this.strategy = b.strategy;
            this.createdAt = b.createdAt == null ? Instant.now() : b.createdAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String analysisId;
            private String projectId;
            private String processId;
            private String stageId;
            private String operationId;
            private String operationType;
            private String requiredAccuracy;
            private String maxDimensions;
            private String material;
            private BigDecimal requiredPowerKw;
            private BigDecimal requiredForceKn;
            private boolean requiredCnc;
            private String gapReason;
            private String severity;
            private String strategy;
            private Instant createdAt;

            public Builder id(String v) { this.id = v; return this; }
            public Builder analysisId(String v) { this.analysisId = v; return this; }
            public Builder projectId(String v) { this.projectId = v; return this; }
            public Builder processId(String v) { this.processId = v; return this; }
            public Builder stageId(String v) { this.stageId = v; return this; }
            public Builder operationId(String v) { this.operationId = v; return this; }
            public Builder operationType(String v) { this.operationType = v; return this; }
            public Builder requiredAccuracy(String v) { this.requiredAccuracy = v; return this; }
            public Builder maxDimensions(String v) { this.maxDimensions = v; return this; }
            public Builder material(String v) { this.material = v; return this; }
            public Builder requiredPowerKw(BigDecimal v) { this.requiredPowerKw = v; return this; }
            public Builder requiredForceKn(BigDecimal v) { this.requiredForceKn = v; return this; }
            public Builder requiredCnc(boolean v) { this.requiredCnc = v; return this; }
            public Builder gapReason(String v) { this.gapReason = v; return this; }
            public Builder severity(String v) { this.severity = v; return this; }
            public Builder strategy(String v) { this.strategy = v; return this; }
            public Builder createdAt(Instant v) { this.createdAt = v; return this; }

            public Gap build() {
                return new Gap(this);
            }
        }

        public String getId() { return id; }
        public String getAnalysisId() { return analysisId; }
        public String getProjectId() { return projectId; }
        public String getProcessId() { return processId; }
        public String getStageId() { return stageId; }
        public String getOperationId() { return operationId; }
        public String getOperationType() { return operationType; }
        public String getRequiredAccuracy() { return requiredAccuracy; }
        public String getMaxDimensions() { return maxDimensions; }
        public String getMaterial() { return material; }
        public BigDecimal getRequiredPowerKw() { return requiredPowerKw; }
        public BigDecimal getRequiredForceKn() { return requiredForceKn; }
        public boolean isRequiredCnc() { return requiredCnc; }
        public String getGapReason() { return gapReason; }
        public String getSeverity() { return severity; }
        public String getStrategy() { return strategy; }
        public Instant getCreatedAt() { return createdAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Gap other)) return false;
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Gap{" +
                    "id='" + id + '\'' +
                    ", operationType='" + operationType + '\'' +
                    ", severity='" + severity + '\'' +
                    ", strategy='" + strategy + '\'' +
                    '}';
        }
    }

    /**
     * Статус gap-анализа.
     */
    public enum GapStatus {
        /** Анализ выполняется. */
        RUNNING,
        /** Анализ успешно завершён. */
        COMPLETED,
        /** Анализ завершился ошибкой. */
        FAILED
    }

    /**
     * Степень критичности дефицита.
     */
    public enum GapSeverity {
        /** Критичный — нет оборудования для ключевой операции. */
        CRITICAL,
        /** Важный — нет для вспомогательной или серьёзное отклонение. */
        MAJOR,
        /** Незначительный — небольшое отклонение. */
        MINOR
    }
}