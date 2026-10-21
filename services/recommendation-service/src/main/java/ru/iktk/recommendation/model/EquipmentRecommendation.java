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
import java.util.Map;
import java.util.Objects;

/**
 * Р”РѕРјРµРЅРЅР°СЏ РјРѕРґРµР»СЊ СЂРµРєРѕРјРµРЅРґР°С†РёРё РїРѕ Р·Р°РєСЂС‹С‚РёСЋ РґРµС„РёС†РёС‚Р° РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ РРљРўРљ.
 *
 * <p>РћРїРёСЃС‹РІР°РµС‚ РѕРґРёРЅ РІР°СЂРёР°РЅС‚ Р·Р°РєСЂС‹С‚РёСЏ РґРµС„РёС†РёС‚Р°, СЃС„РѕСЂРјРёСЂРѕРІР°РЅРЅС‹Р№
 * {@code EquipmentRecommendationService} (РїРѕР·РёС†РёСЏ 96):
 * <ul>
 *   <li>{@link RecommendationType#ALTERNATIVE} вЂ” Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅРѕРµ
 *       РёРјРµСЋС‰РµРµСЃСЏ РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ РїСЂРµРґРїСЂРёСЏС‚РёСЏ;</li>
 *   <li>{@link RecommendationType#PURCHASE} вЂ” РґРѕРєСѓРїРєР° РЅРѕРІРѕРіРѕ
 *       РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ;</li>
 *   <li>{@link RecommendationType#RENT} вЂ” Р°СЂРµРЅРґР° РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ;</li>
 *   <li>{@link RecommendationType#OUTSOURCE} вЂ” РїРµСЂРµРґР°С‡Р° РѕРїРµСЂР°С†РёРё
 *       РЅР° Р°СѓС‚СЃРѕСЂСЃ;</li>
 *   <li>{@link RecommendationType#ADJUST_PROCESS} вЂ” РєРѕСЂСЂРµРєС‚РёСЂРѕРІРєР°
 *       РўРџ РїРѕРґ РёРјРµСЋС‰РёР№СЃСЏ РїР°СЂРє.</li>
 * </ul>
 *
 * <p><b>РЎС‚Р°С‚СѓСЃС‹:</b>
 * <ul>
 *   <li>{@link RecommendationStatus#PENDING} вЂ” РїСЂРµРґР»РѕР¶РµРЅР°, РѕР¶РёРґР°РµС‚
 *       СЂРµС€РµРЅРёСЏ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ РЅР° С‚РѕС‡РєРµ РєРѕРЅС‚СЂРѕР»СЏ
 *       {@code RECOMMENDATION_REVIEW};</li>
 *   <li>{@link RecommendationStatus#ACCEPTED} вЂ” РїСЂРёРЅСЏС‚Р°, РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ
 *       РЅР°Р·РЅР°С‡РµРЅРѕ, РїСЂРѕРµРєС‚ РїСЂРѕРґРѕР»Р¶Р°РµС‚СЃСЏ;</li>
 *   <li>{@link RecommendationStatus#REJECTED} вЂ” РѕС‚РєР»РѕРЅРµРЅР°.</li>
 * </ul>
 *
 * <p><b>РљР»СЋС‡РµРІС‹Рµ Р°С‚СЂРёР±СѓС‚С‹:</b>
 * <ul>
 *   <li>{@code projectId} вЂ” РїСЂРѕРµРєС‚;</li>
 *   <li>{@code gapId} вЂ” РґРµС„РёС†РёС‚, Рє РєРѕС‚РѕСЂРѕРјСѓ РѕС‚РЅРѕСЃРёС‚СЃСЏ СЂРµРєРѕРјРµРЅРґР°С†РёСЏ;</li>
 *   <li>{@code type} вЂ” С‚РёРї СЂРµРєРѕРјРµРЅРґР°С†РёРё;</li>
 *   <li>{@code equipmentId} вЂ” РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ
 *       (РґР»СЏ ALTERNATIVE);</li>
 *   <li>{@code vendorId} вЂ” РёРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РїРѕСЃС‚Р°РІС‰РёРєР°
 *       (РґР»СЏ PURCHASE / RENT);</li>
 *   <li>{@code rationale} вЂ” РѕР±РѕСЃРЅРѕРІР°РЅРёРµ;</li>
 *   <li>{@code score} вЂ” РёРЅС‚РµРіСЂР°Р»СЊРЅС‹Р№ СЃРєРѕСЂ (0вЂ¦1);</li>
 *   <li>{@code estimatedCost} вЂ” РѕС†РµРЅРѕС‡РЅР°СЏ СЃС‚РѕРёРјРѕСЃС‚СЊ;</li>
 *   <li>{@code leadTimeDays} вЂ” СЃСЂРѕРє СЂРµР°Р»РёР·Р°С†РёРё (РґРЅРё);</li>
 *   <li>{@code status} вЂ” СЃС‚Р°С‚СѓСЃ;</li>
 *   <li>{@code acceptedBy}, {@code acceptedAt} вЂ” РєС‚Рѕ Рё РєРѕРіРґР° РїСЂРёРЅСЏР»;</li>
 *   <li>{@code parameters} вЂ” РїСЂРѕРёР·РІРѕР»СЊРЅС‹Рµ РїР°СЂР°РјРµС‚СЂС‹ (JSONB).</li>
 * </ul>
 *
 * <p>РўР°Р±Р»РёС†Р°: {@code equipment_recommendations} (PostgreSQL, РјРёРіСЂР°С†РёСЏ V6).
 */
@Entity
@Table(
        name = "equipment_recommendations",
        indexes = {
                @Index(name = "idx_rec_project",        columnList = "project_id"),
                @Index(name = "idx_rec_gap",            columnList = "gap_id"),
                @Index(name = "idx_rec_type",           columnList = "type"),
                @Index(name = "idx_rec_status",         columnList = "status"),
                @Index(name = "idx_rec_equipment",      columnList = "equipment_id"),
                @Index(name = "idx_rec_vendor",         columnList = "vendor_id"),
                @Index(name = "idx_rec_score",          columnList = "score"),
                @Index(name = "idx_rec_created_at",     columnList = "created_at DESC")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentRecommendation {

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

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РґРµС„РёС†РёС‚Р°. */
    @Column(name = "gap_id", length = 64)
    private String gapId;

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ gap-Р°РЅР°Р»РёР·Р°, РІ СЂР°РјРєР°С… РєРѕС‚РѕСЂРѕРіРѕ СЃС„РѕСЂРјРёСЂРѕРІР°РЅР° СЂРµРєРѕРјРµРЅРґР°С†РёСЏ. */
    @Column(name = "analysis_id", length = 64)
    private String analysisId;

    // ------------------------------------------------------------------
    // РўРёРї Рё СЃС‚Р°С‚СѓСЃ
    // ------------------------------------------------------------------

    /** РўРёРї СЂРµРєРѕРјРµРЅРґР°С†РёРё: ALTERNATIVE, PURCHASE, RENT, OUTSOURCE, ADJUST_PROCESS. */
    @NotBlank
    @Size(max = 32)
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** РЎС‚Р°С‚СѓСЃ: PENDING, ACCEPTED, REJECTED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = RecommendationStatus.PENDING.name();

    // ------------------------------------------------------------------
    // РЎСЃС‹Р»РєРё РЅР° РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ Рё РїРѕСЃС‚Р°РІС‰РёРєР°
    // ------------------------------------------------------------------

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ (РґР»СЏ ALTERNATIVE). */
    @Size(max = 64)
    @Column(name = "equipment_id", length = 64)
    private String equipmentId;

    /** РРґРµРЅС‚РёС„РёРєР°С‚РѕСЂ РїРѕСЃС‚Р°РІС‰РёРєР° (РґР»СЏ PURCHASE / RENT). */
    @Column(name = "vendor_id", length = 64)
    private String vendorId;

    // ------------------------------------------------------------------
    // РћР±РѕСЃРЅРѕРІР°РЅРёРµ Рё РјРµС‚СЂРёРєРё
    // ------------------------------------------------------------------

    /** РћР±РѕСЃРЅРѕРІР°РЅРёРµ СЂРµРєРѕРјРµРЅРґР°С†РёРё (С‡РµР»РѕРІРµРєРѕС‡РёС‚Р°РµРјРѕРµ). */
    @Size(max = 2000)
    @Column(name = "rationale", length = 2000)
    private String rationale;

    /** РЎРєРѕСЂ (0вЂ¦1). */
    @Column(name = "score", precision = 8, scale = 4)
    private BigDecimal score;

    /** РћС†РµРЅРѕС‡РЅР°СЏ СЃС‚РѕРёРјРѕСЃС‚СЊ. */
    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    /** РЎСЂРѕРє СЂРµР°Р»РёР·Р°С†РёРё (РґРЅРё). */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    // ------------------------------------------------------------------
    // РџСЂРёРЅСЏС‚РёРµ СЂРµС€РµРЅРёСЏ
    // ------------------------------------------------------------------

    /** РљС‚Рѕ РїСЂРёРЅСЏР» СЂРµС€РµРЅРёРµ. */
    @Size(max = 128)
    @Column(name = "accepted_by", length = 128)
    private String acceptedBy;

    /** РљРѕРіРґР° РїСЂРёРЅСЏС‚Рѕ. */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    // ------------------------------------------------------------------
    // Р”РѕРїРѕР»РЅРёС‚РµР»СЊРЅРѕ
    // ------------------------------------------------------------------

    /** РџСЂРѕРёР·РІРѕР»СЊРЅС‹Рµ РїР°СЂР°РјРµС‚СЂС‹ (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    // ------------------------------------------------------------------
    // РЎР»СѓР¶РµР±РЅС‹Рµ РїРѕР»СЏ
    // ------------------------------------------------------------------

    /** РњРѕРјРµРЅС‚ СЃРѕР·РґР°РЅРёСЏ. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** РњРѕРјРµРЅС‚ РїРѕСЃР»РµРґРЅРµРіРѕ РёР·РјРµРЅРµРЅРёСЏ. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // РљРѕРЅСЃС‚СЂСѓРєС‚РѕСЂС‹
    // ------------------------------------------------------------------

    protected EquipmentRecommendation() {
    }

    public EquipmentRecommendation(String id, String projectId,
                                   String gapId, RecommendationType type) {
        this.id = id;
        this.projectId = projectId;
        this.gapId = gapId;
        this.type = type.name();
        this.status = RecommendationStatus.PENDING.name();
        Instant now = Instant.now();
        this.createdAt = now;
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
        private String gapId;
        private String analysisId;
        private String type;
        private String status;
        private String equipmentId;
        private String vendorId;
        private String rationale;
        private BigDecimal score;
        private BigDecimal estimatedCost;
        private Integer leadTimeDays;
        private String acceptedBy;
        private Instant acceptedAt;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder gapId(String v) { this.gapId = v; return this; }
        public Builder analysisId(String v) { this.analysisId = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder type(RecommendationType v) { this.type = v == null ? null : v.name(); return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder status(RecommendationStatus v) { this.status = v == null ? null : v.name(); return this; }
        public Builder equipmentId(String v) { this.equipmentId = v; return this; }
        public Builder vendorId(String v) { this.vendorId = v; return this; }
        public Builder rationale(String v) { this.rationale = v; return this; }
        public Builder score(BigDecimal v) { this.score = v; return this; }
        public Builder estimatedCost(BigDecimal v) { this.estimatedCost = v; return this; }
        public Builder leadTimeDays(Integer v) { this.leadTimeDays = v; return this; }
        public Builder acceptedBy(String v) { this.acceptedBy = v; return this; }
        public Builder acceptedAt(Instant v) { this.acceptedAt = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public EquipmentRecommendation build() {
            EquipmentRecommendation r = new EquipmentRecommendation();
            r.id = this.id;
            r.projectId = this.projectId;
            r.gapId = this.gapId;
            r.analysisId = this.analysisId;
            r.type = this.type;
            r.status = this.status == null ? RecommendationStatus.PENDING.name() : this.status;
            r.equipmentId = this.equipmentId;
            r.vendorId = this.vendorId;
            r.rationale = this.rationale;
            r.score = this.score;
            r.estimatedCost = this.estimatedCost;
            r.leadTimeDays = this.leadTimeDays;
            r.acceptedBy = this.acceptedBy;
            r.acceptedAt = this.acceptedAt;
            r.parameters = this.parameters;
            Instant now = Instant.now();
            r.createdAt = this.createdAt == null ? now : this.createdAt;
            r.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return r;
        }
    }

    // ------------------------------------------------------------------
    // Р‘РёР·РЅРµСЃ-РјРµС‚РѕРґС‹
    // ------------------------------------------------------------------

    /** РџСЂРёРЅСЏС‚СЊ СЂРµРєРѕРјРµРЅРґР°С†РёСЋ. */
    public void accept(String acceptedBy) {
        this.status = RecommendationStatus.ACCEPTED.name();
        this.acceptedBy = acceptedBy;
        this.acceptedAt = Instant.now();
        touch();
    }

    /** РћС‚РєР»РѕРЅРёС‚СЊ СЂРµРєРѕРјРµРЅРґР°С†РёСЋ. */
    public void reject(String reason) {
        this.status = RecommendationStatus.REJECTED.name();
        this.rationale = (this.rationale == null ? "" : this.rationale + " | ")
                                + "Отклонено: " + (reason == null ? "" : reason);
        touch();
    }

    /** РћР¶РёРґР°РµС‚ Р»Рё СЂРµРєРѕРјРµРЅРґР°С†РёСЏ СЂРµС€РµРЅРёСЏ. */
    public boolean isPending() {
        return RecommendationStatus.PENDING.name().equals(this.status);
    }

    /** РџСЂРёРЅСЏС‚Р° Р»Рё СЂРµРєРѕРјРµРЅРґР°С†РёСЏ. */
    public boolean isAccepted() {
        return RecommendationStatus.ACCEPTED.name().equals(this.status);
    }

    /** РћС‚РєР»РѕРЅРµРЅР° Р»Рё СЂРµРєРѕРјРµРЅРґР°С†РёСЏ. */
    public boolean isRejected() {
        return RecommendationStatus.REJECTED.name().equals(this.status);
    }

    /** РЇРІР»СЏРµС‚СЃСЏ Р»Рё СЂРµРєРѕРјРµРЅРґР°С†РёСЏ Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРѕР№ РЅР° РїСЂРµРґРїСЂРёСЏС‚РёРё. */
    public boolean isAlternative() {
        return RecommendationType.ALTERNATIVE.name().equals(this.type);
    }

    /** РўСЂРµР±СѓРµС‚СЃСЏ Р»Рё РґРѕРєСѓРїРєР°/Р°СЂРµРЅРґР°. */
    public boolean requiresPurchase() {
        return RecommendationType.PURCHASE.name().equals(this.type)
                || RecommendationType.RENT.name().equals(this.type);
    }

    /** РЎРІСЏР·Р°РЅР° Р»Рё СЃ РІРЅРµС€РЅРёРј РїРѕСЃС‚Р°РІС‰РёРєРѕРј. */
    public boolean hasVendor() {
        return this.vendorId != null && !this.vendorId.isBlank();
    }

    /** РЎРІСЏР·Р°РЅР° Р»Рё СЃ РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµРј РЅР° РїСЂРµРґРїСЂРёСЏС‚РёРё. */
    public boolean hasEquipment() {
        return this.equipmentId != null && !this.equipmentId.isBlank();
    }

    /** РџРѕР»СѓС‡РёС‚СЊ С‚РёРї РєР°Рє enum. */
    public RecommendationType getTypeEnum() {
        try {
            return RecommendationType.valueOf(this.type);
        } catch (Exception e) {
            return RecommendationType.ALTERNATIVE;
        }
    }

    /** РџРѕР»СѓС‡РёС‚СЊ СЃС‚Р°С‚СѓСЃ РєР°Рє enum. */
    public RecommendationStatus getStatusEnum() {
        try {
            return RecommendationStatus.valueOf(this.status);
        } catch (Exception e) {
            return RecommendationStatus.PENDING;
        }
    }

    /** РћР±РЅРѕРІРёС‚СЊ РјРµС‚РєСѓ РІСЂРµРјРµРЅРё. */
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

    public String getGapId() { return gapId; }
    public void setGapId(String gapId) { this.gapId = gapId; touch(); }

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; touch(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; touch(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }

    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; touch(); }

    public String getVendorId() { return vendorId; }
    public void setVendorId(String vendorId) { this.vendorId = vendorId; touch(); }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; touch(); }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; touch(); }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; touch(); }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; touch(); }

    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; touch(); }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EquipmentRecommendation other)) {
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
        return "EquipmentRecommendation{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", gapId='" + gapId + '\'' +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                ", equipmentId='" + equipmentId + '\'' +
                ", vendorId='" + vendorId + '\'' +
                ", score=" + score +
                '}';
    }

    // ------------------------------------------------------------------
    // Р’Р»РѕР¶РµРЅРЅС‹Рµ enum'С‹
    // ------------------------------------------------------------------

    /**
     * РўРёРї СЂРµРєРѕРјРµРЅРґР°С†РёРё.
     */
    public enum RecommendationType {
        /** РђР»СЊС‚РµСЂРЅР°С‚РёРІРЅРѕРµ РёРјРµСЋС‰РµРµСЃСЏ РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ РїСЂРµРґРїСЂРёСЏС‚РёСЏ. */
        ALTERNATIVE,
        /** Р”РѕРєСѓРїРєР° РЅРѕРІРѕРіРѕ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ. */
        PURCHASE,
        /** РђСЂРµРЅРґР° РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ. */
        RENT,
        /** РџРµСЂРµРґР°С‡Р° РѕРїРµСЂР°С†РёРё РЅР° Р°СѓС‚СЃРѕСЂСЃ. */
        OUTSOURCE,
        /** РљРѕСЂСЂРµРєС‚РёСЂРѕРІРєР° РўРџ РїРѕРґ РёРјРµСЋС‰РёР№СЃСЏ РїР°СЂРє. */
        ADJUST_PROCESS
    }

    /**
     * РЎС‚Р°С‚СѓСЃ СЂРµРєРѕРјРµРЅРґР°С†РёРё.
     */
    public enum RecommendationStatus {
        /** РџСЂРµРґР»РѕР¶РµРЅР°, РѕР¶РёРґР°РµС‚ СЂРµС€РµРЅРёСЏ. */
        PENDING,
        /** РџСЂРёРЅСЏС‚Р°, РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ РЅР°Р·РЅР°С‡РµРЅРѕ. */
        ACCEPTED,
        /** РћС‚РєР»РѕРЅРµРЅР°. */
        REJECTED
    }
}