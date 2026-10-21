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
 * Р”РѕРјРµРЅРЅР°СЏ РјРѕРґРµР»СЊ gap-Р°РЅР°Р»РёР·Р° РРљРўРљ.
 *
 * <p>РћРїРёСЃС‹РІР°РµС‚ СЂРµР·СѓР»СЊС‚Р°С‚ СЃРѕРїРѕСЃС‚Р°РІР»РµРЅРёСЏ С‚СЂРµР±РѕРІР°РЅРёР№ Рє РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЋ
 * (РёР· СЃРїСЂРѕРµРєС‚РёСЂРѕРІР°РЅРЅС‹С… РўРџ) СЃ С„Р°РєС‚РёС‡РµСЃРєРёРј РїР°СЂРєРѕРј РїСЂРµРґРїСЂРёСЏС‚РёСЏ.
 * РҐСЂР°РЅРёС‚ СЃРїРёСЃРѕРє РІС‹СЏРІР»РµРЅРЅС‹С… РґРµС„РёС†РёС‚РѕРІ ({@link Gap}) Рё Р°РіСЂРµРіРёСЂРѕРІР°РЅРЅС‹Рµ
 * РїРѕРєР°Р·Р°С‚РµР»Рё.
 *
 * <p><b>РљР»СЋС‡РµРІС‹Рµ Р°С‚СЂРёР±СѓС‚С‹:</b>
 * <ul>
 *   <li>{@code projectId} вЂ” РїСЂРѕРµРєС‚;</li>
 *   <li>{@code strategy} вЂ” СЃС‚СЂР°С‚РµРіРёСЏ СЃРѕРїРѕСЃС‚Р°РІР»РµРЅРёСЏ
 *       (STRICT, FLEXIBLE, HYBRID);</li>
 *   <li>{@code status} вЂ” RUNNING, COMPLETED, FAILED;</li>
 *   <li>{@code gapsCount} вЂ” С‡РёСЃР»Рѕ РІС‹СЏРІР»РµРЅРЅС‹С… РґРµС„РёС†РёС‚РѕРІ;</li>
 *   <li>{@code recommendationsCount} вЂ” С‡РёСЃР»Рѕ СЃС„РѕСЂРјРёСЂРѕРІР°РЅРЅС‹С…
 *       СЂРµРєРѕРјРµРЅРґР°С†РёР№;</li>
 *   <li>{@code gaps} вЂ” СЃРїРёСЃРѕРє РґРµС„РёС†РёС‚РѕРІ ({@link Gap}, JSONB);</li>
 *   <li>{@code startedAt}, {@code completedAt} вЂ” РІСЂРµРјРµРЅРЅС‹Рµ РјРµС‚РєРё;</li>
 *   <li>{@code error} вЂ” СЃРѕРѕР±С‰РµРЅРёРµ РѕР± РѕС€РёР±РєРµ (РїСЂРё FAILED).</li>
 * </ul>
 *
 * <p><b>Р–РёР·РЅРµРЅРЅС‹Р№ С†РёРєР»:</b>
 * <ol>
 *   <li>{@code RUNNING} вЂ” Р°РЅР°Р»РёР· Р·Р°РїСѓС‰РµРЅ (СЃРѕР·РґР°РЅРёРµ Р·Р°РїРёСЃРё);</li>
 *   <li>{@code COMPLETED} вЂ” Р°РЅР°Р»РёР· Р·Р°РІРµСЂС€С‘РЅ, РґРµС„РёС†РёС‚С‹ Рё СЂРµРєРѕРјРµРЅРґР°С†РёРё
 *       Р·Р°С„РёРєСЃРёСЂРѕРІР°РЅС‹;</li>
 *   <li>{@code FAILED} вЂ” РѕС€РёР±РєР°, {@code error} Р·Р°РїРѕР»РЅРµРЅ.</li>
 * </ol>
 *
 * <p>РўР°Р±Р»РёС†Р°: {@code equipment_gap_analysis} (PostgreSQL, РјРёРіСЂР°С†РёСЏ V6).
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
    // РРґРµРЅС‚РёС„РёРєР°С†РёСЏ
    // ------------------------------------------------------------------

    /** РЈРЅРёРєР°Р»СЊРЅС‹Р№ РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РїСЂРѕРµРєС‚Р°. */
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    // ------------------------------------------------------------------
    // РџР°СЂР°РјРµС‚СЂС‹ Р°РЅР°Р»РёР·Р°
    // ------------------------------------------------------------------

    /** РЎС‚СЂР°С‚РµРіРёСЏ СЃРѕРїРѕСЃС‚Р°РІР»РµРЅРёСЏ: STRICT, FLEXIBLE, HYBRID. */
    @NotBlank
    @Size(max = 32)
    @Column(name = "strategy", nullable = false, length = 32)
    private String strategy;

    /** РЎС‚Р°С‚СѓСЃ: RUNNING, COMPLETED, FAILED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** РљС‚Рѕ Р·Р°РїСѓСЃС‚РёР» Р°РЅР°Р»РёР·. */
    @Size(max = 128)
    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    // ------------------------------------------------------------------
    // Р РµР·СѓР»СЊС‚Р°С‚С‹
    // ------------------------------------------------------------------

    /** Р§РёСЃР»Рѕ РІС‹СЏРІР»РµРЅРЅС‹С… РґРµС„РёС†РёС‚РѕРІ. */
    @Column(name = "gaps_count", nullable = false)
    private int gapsCount = 0;

    /** Р§РёСЃР»Рѕ СЃС„РѕСЂРјРёСЂРѕРІР°РЅРЅС‹С… СЂРµРєРѕРјРµРЅРґР°С†РёР№. */
    @Column(name = "recommendations_count", nullable = false)
    private int recommendationsCount = 0;

    /**
     * РЎРїРёСЃРѕРє РґРµС„РёС†РёС‚РѕРІ ({@link Gap}).
     * РҐСЂР°РЅРёС‚СЃСЏ РєР°Рє JSONB вЂ” РґР»СЏ РіРёР±РєРѕСЃС‚Рё Рё СЃРєРѕСЂРѕСЃС‚Рё.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gaps", columnDefinition = "jsonb")
    private List<Gap> gaps = new ArrayList<>();

    /** РЎРІРѕРґРЅС‹Рµ РјРµС‚СЂРёРєРё (JSONB): bySeverity, byOperationType Рё С‚.Рї. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary;

    // ------------------------------------------------------------------
    // РЎР»СѓР¶РµР±РЅС‹Рµ РїРѕР»СЏ
    // ------------------------------------------------------------------

    /** РњРѕРјРµРЅС‚ Р·Р°РїСѓСЃРєР°. */
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** РњРѕРјРµРЅС‚ Р·Р°РІРµСЂС€РµРЅРёСЏ. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** РЎРѕРѕР±С‰РµРЅРёРµ РѕР± РѕС€РёР±РєРµ (РїСЂРё FAILED). */
    @Size(max = 2000)
    @Column(name = "error", length = 2000)
    private String error;

    /** РњРѕРјРµРЅС‚ РїРѕСЃР»РµРґРЅРµРіРѕ РёР·РјРµРЅРµРЅРёСЏ. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // РљРѕРЅСЃС‚СЂСѓРєС‚РѕСЂС‹
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
    // Р‘РёР·РЅРµСЃ-РјРµС‚РѕРґС‹
    // ------------------------------------------------------------------

    /** РџРѕРјРµС‚РёС‚СЊ Р°РЅР°Р»РёР· РєР°Рє РІС‹РїРѕР»РЅСЏСЋС‰РёР№СЃСЏ. */
    public void markRunning() {
        this.status = GapStatus.RUNNING.name();
        touch();
    }

    /** РџРѕРјРµС‚РёС‚СЊ Р°РЅР°Р»РёР· РєР°Рє СѓСЃРїРµС€РЅРѕ Р·Р°РІРµСЂС€С‘РЅРЅС‹Р№. */
    public void markCompleted(int gapsCount, int recommendationsCount) {
        this.status = GapStatus.COMPLETED.name();
        this.gapsCount = gapsCount;
        this.recommendationsCount = recommendationsCount;
        this.completedAt = Instant.now();
        touch();
    }

    /** РџРѕРјРµС‚РёС‚СЊ Р°РЅР°Р»РёР· РєР°Рє СѓРїР°РІС€РёР№. */
    public void markFailed(String error) {
        this.status = GapStatus.FAILED.name();
        this.error = error;
        this.completedAt = Instant.now();
        touch();
    }

    /** Р—Р°РІРµСЂС€С‘РЅ Р»Рё Р°РЅР°Р»РёР·. */
    public boolean isCompleted() {
        return GapStatus.COMPLETED.name().equals(this.status);
    }

    /** РџСЂРѕРІР°Р»РµРЅ Р»Рё Р°РЅР°Р»РёР·. */
    public boolean isFailed() {
        return GapStatus.FAILED.name().equals(this.status);
    }

    /** Р’С‹РїРѕР»РЅСЏРµС‚СЃСЏ Р»Рё Р°РЅР°Р»РёР·. */
    public boolean isRunning() {
        return GapStatus.RUNNING.name().equals(this.status);
    }

    /** Р•СЃС‚СЊ Р»Рё РґРµС„РёС†РёС‚С‹. */
    public boolean hasGaps() {
        return gapsCount > 0;
    }

    /** Р”Р»РёС‚РµР»СЊРЅРѕСЃС‚СЊ Р°РЅР°Р»РёР·Р° РІ РјРёР»Р»РёСЃРµРєСѓРЅРґР°С…. */
    public long durationMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return java.time.Duration.between(startedAt, completedAt).toMillis();
    }

    /** Р”РѕР±Р°РІРёС‚СЊ РґРµС„РёС†РёС‚. */
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

    /** РћР±РЅРѕРІРёС‚СЊ РјРµС‚РєСѓ РІСЂРµРјРµРЅРё. */
    public String getOperationType() {
        if (gaps == null || gaps.isEmpty()) return null;
        return gaps.get(0).getOperationType();
    }

    public java.util.List<java.util.Map<String, Object>> getRequirements() {
        return java.util.Collections.emptyList();
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Р“РµС‚С‚РµСЂС‹ Рё СЃРµС‚С‚РµСЂС‹
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
    // Р’Р»РѕР¶РµРЅРЅС‹Рµ С‚РёРїС‹: Gap Рё GapStatus
    // ------------------------------------------------------------------

    /**
     * Р”РµС„РёС†РёС‚ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ вЂ” СЂРµР·СѓР»СЊС‚Р°С‚ СЃРѕРїРѕСЃС‚Р°РІР»РµРЅРёСЏ РѕРґРЅРѕРіРѕ С‚СЂРµР±РѕРІР°РЅРёСЏ
     * СЃ РїР°СЂРєРѕРј.
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
     * РЎС‚Р°С‚СѓСЃ gap-Р°РЅР°Р»РёР·Р°.
     */
    public enum GapStatus {
        /** РђРЅР°Р»РёР· РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ. */
        RUNNING,
        /** РђРЅР°Р»РёР· СѓСЃРїРµС€РЅРѕ Р·Р°РІРµСЂС€С‘РЅ. */
        COMPLETED,
        /** РђРЅР°Р»РёР· Р·Р°РІРµСЂС€РёР»СЃСЏ РѕС€РёР±РєРѕР№. */
        FAILED
    }

    /**
     * РЎС‚РµРїРµРЅСЊ РєСЂРёС‚РёС‡РЅРѕСЃС‚Рё РґРµС„РёС†РёС‚Р°.
     */
    public enum GapSeverity {
        /** РљСЂРёС‚РёС‡РЅС‹Р№ вЂ” РЅРµС‚ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ РґР»СЏ РєР»СЋС‡РµРІРѕР№ РѕРїРµСЂР°С†РёРё. */
        CRITICAL,
        /** Р’Р°Р¶РЅС‹Р№ вЂ” РЅРµС‚ РґР»СЏ РІСЃРїРѕРјРѕРіР°С‚РµР»СЊРЅРѕР№ РёР»Рё СЃРµСЂСЊС‘Р·РЅРѕРµ РѕС‚РєР»РѕРЅРµРЅРёРµ. */
        MAJOR,
        /** РќРµР·РЅР°С‡РёС‚РµР»СЊРЅС‹Р№ вЂ” РЅРµР±РѕР»СЊС€РѕРµ РѕС‚РєР»РѕРЅРµРЅРёРµ. */
        MINOR
    }
}