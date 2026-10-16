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
 * Доменная модель рекомендации по закрытию дефицита оборудования ИКТК.
 *
 * <p>Описывает один вариант закрытия дефицита, сформированный
 * {@code EquipmentRecommendationService} (позиция 96):
 * <ul>
 *   <li>{@link RecommendationType#ALTERNATIVE} — альтернативное
 *       имеющееся оборудование предприятия;</li>
 *   <li>{@link RecommendationType#PURCHASE} — докупка нового
 *       оборудования;</li>
 *   <li>{@link RecommendationType#RENT} — аренда оборудования;</li>
 *   <li>{@link RecommendationType#OUTSOURCE} — передача операции
 *       на аутсорс;</li>
 *   <li>{@link RecommendationType#ADJUST_PROCESS} — корректировка
 *       ТП под имеющийся парк.</li>
 * </ul>
 *
 * <p><b>Статусы:</b>
 * <ul>
 *   <li>{@link RecommendationStatus#PENDING} — предложена, ожидает
 *       решения пользователя на точке контроля
 *       {@code RECOMMENDATION_REVIEW};</li>
 *   <li>{@link RecommendationStatus#ACCEPTED} — принята, оборудование
 *       назначено, проект продолжается;</li>
 *   <li>{@link RecommendationStatus#REJECTED} — отклонена.</li>
 * </ul>
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code projectId} — проект;</li>
 *   <li>{@code gapId} — дефицит, к которому относится рекомендация;</li>
 *   <li>{@code type} — тип рекомендации;</li>
 *   <li>{@code equipmentId} — идентификатор оборудования
 *       (для ALTERNATIVE);</li>
 *   <li>{@code vendorId} — идентификатор поставщика
 *       (для PURCHASE / RENT);</li>
 *   <li>{@code rationale} — обоснование;</li>
 *   <li>{@code score} — интегральный скор (0…1);</li>
 *   <li>{@code estimatedCost} — оценочная стоимость;</li>
 *   <li>{@code leadTimeDays} — срок реализации (дни);</li>
 *   <li>{@code status} — статус;</li>
 *   <li>{@code acceptedBy}, {@code acceptedAt} — кто и когда принял;</li>
 *   <li>{@code parameters} — произвольные параметры (JSONB).</li>
 * </ul>
 *
 * <p>Таблица: {@code equipment_recommendations} (PostgreSQL, миграция V6).
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
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор проекта. */
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    /** Идентификатор дефицита. */
    @Column(name = "gap_id", length = 64)
    private String gapId;

    /** Идентификатор gap-анализа, в рамках которого сформирована рекомендация. */
    @Column(name = "analysis_id", length = 64)
    private String analysisId;

    // ------------------------------------------------------------------
    // Тип и статус
    // ------------------------------------------------------------------

    /** Тип рекомендации: ALTERNATIVE, PURCHASE, RENT, OUTSOURCE, ADJUST_PROCESS. */
    @NotBlank
    @Size(max = 32)
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** Статус: PENDING, ACCEPTED, REJECTED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = RecommendationStatus.PENDING.name();

    // ------------------------------------------------------------------
    // Ссылки на оборудование и поставщика
    // ------------------------------------------------------------------

    /** Идентификатор оборудования (для ALTERNATIVE). */
    @Size(max = 64)
    @Column(name = "equipment_id", length = 64)
    private String equipmentId;

    /** Идентификатор поставщика (для PURCHASE / RENT). */
    @Column(name = "vendor_id", length = 64)
    private String vendorId;

    // ------------------------------------------------------------------
    // Обоснование и метрики
    // ------------------------------------------------------------------

    /** Обоснование рекомендации (человекочитаемое). */
    @Size(max = 2000)
    @Column(name = "rationale", length = 2000)
    private String rationale;

    /** Скор (0…1). */
    @Column(name = "score", precision = 8, scale = 4)
    private BigDecimal score;

    /** Оценочная стоимость. */
    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    /** Срок реализации (дни). */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    // ------------------------------------------------------------------
    // Принятие решения
    // ------------------------------------------------------------------

    /** Кто принял решение. */
    @Size(max = 128)
    @Column(name = "accepted_by", length = 128)
    private String acceptedBy;

    /** Когда принято. */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    // ------------------------------------------------------------------
    // Дополнительно
    // ------------------------------------------------------------------

    /** Произвольные параметры (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    // ------------------------------------------------------------------
    // Служебные поля
    // ------------------------------------------------------------------

    /** Момент создания. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
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
    // Бизнес-методы
    // ------------------------------------------------------------------

    /** Принять рекомендацию. */
    public void accept(String acceptedBy) {
        this.status = RecommendationStatus.ACCEPTED.name();
        this.acceptedBy = acceptedBy;
        this.acceptedAt = Instant.now();
        touch();
    }

    /** Отклонить рекомендацию. */
    public void reject(String reason) {
        this.status = RecommendationStatus.REJECTED.name();
        this.rationale = (this.rationale == null ? "" : this.rationale + " | ")
                + "Отклонено: " + (reason == null ? "" : reason);
        touch();
    }

    /** Ожидает ли рекомендация решения. */
    public boolean isPending() {
        return RecommendationStatus.PENDING.name().equals(this.status);
    }

    /** Принята ли рекомендация. */
    public boolean isAccepted() {
        return RecommendationStatus.ACCEPTED.name().equals(this.status);
    }

    /** Отклонена ли рекомендация. */
    public boolean isRejected() {
        return RecommendationStatus.REJECTED.name().equals(this.status);
    }

    /** Является ли рекомендация альтернативой на предприятии. */
    public boolean isAlternative() {
        return RecommendationType.ALTERNATIVE.name().equals(this.type);
    }

    /** Требуется ли докупка/аренда. */
    public boolean requiresPurchase() {
        return RecommendationType.PURCHASE.name().equals(this.type)
                || RecommendationType.RENT.name().equals(this.type);
    }

    /** Связана ли с внешним поставщиком. */
    public boolean hasVendor() {
        return this.vendorId != null && !this.vendorId.isBlank();
    }

    /** Связана ли с оборудованием на предприятии. */
    public boolean hasEquipment() {
        return this.equipmentId != null && !this.equipmentId.isBlank();
    }

    /** Получить тип как enum. */
    public RecommendationType getTypeEnum() {
        try {
            return RecommendationType.valueOf(this.type);
        } catch (Exception e) {
            return RecommendationType.ALTERNATIVE;
        }
    }

    /** Получить статус как enum. */
    public RecommendationStatus getStatusEnum() {
        try {
            return RecommendationStatus.valueOf(this.status);
        } catch (Exception e) {
            return RecommendationStatus.PENDING;
        }
    }

    /** Обновить метку времени. */
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
    // Вложенные enum'ы
    // ------------------------------------------------------------------

    /**
     * Тип рекомендации.
     */
    public enum RecommendationType {
        /** Альтернативное имеющееся оборудование предприятия. */
        ALTERNATIVE,
        /** Докупка нового оборудования. */
        PURCHASE,
        /** Аренда оборудования. */
        RENT,
        /** Передача операции на аутсорс. */
        OUTSOURCE,
        /** Корректировка ТП под имеющийся парк. */
        ADJUST_PROCESS
    }

    /**
     * Статус рекомендации.
     */
    public enum RecommendationStatus {
        /** Предложена, ожидает решения. */
        PENDING,
        /** Принята, оборудование назначено. */
        ACCEPTED,
        /** Отклонена. */
        REJECTED
    }
}