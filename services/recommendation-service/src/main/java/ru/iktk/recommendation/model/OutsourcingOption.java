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
 * Доменная модель варианта аутсорса ИКТК.
 *
 * <p>Описывает один вариант передачи операции на аутсорс при дефиците
 * оборудования. Формируется {@code OutsourcingService} (позиция 98)
 * на основе дефицита ({@code gapId}) и используется на точке контроля
 * {@code RECOMMENDATION_REVIEW} оркестратора.
 *
 * <p><b>Виды аутсорса:</b>
 * <ul>
 *   <li>{@link OutsourcingType#CONTRACTOR} — передача операции
 *       стороннему подрядчику;</li>
 *   <li>{@link OutsourcingType#PARTNER_PLANT} — передача на
 *       завод-партнёр по договору кооперации;</li>
 *   <li>{@link OutsourcingType#RENTAL} — аренда оборудования
 *       с оператором;</li>
 *   <li>{@link OutsourcingType#COOPERATION} — кооперация с другим
 *       предприятием.</li>
 * </ul>
 *
 * <p><b>Статусы:</b>
 * <ul>
 *   <li>{@link OutsourcingStatus#PENDING} — предложен, ожидает
 *       решения;</li>
 *   <li>{@link OutsourcingStatus#SELECTED} — выбран пользователем,
 *       остальные варианты по этому дефициту отклоняются;</li>
 *   <li>{@link OutsourcingStatus#REJECTED} — отклонён.</li>
 * </ul>
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code gapId} — дефицит, к которому относится вариант;</li>
 *   <li>{@code projectId} — проект;</li>
 *   <li>{@code type} — вид аутсорса;</li>
 *   <li>{@code contractor} — наименование подрядчика;</li>
 *   <li>{@code operation} — операция, передаваемая на аутсорс;</li>
 *   <li>{@code cost} — стоимость (руб.);</li>
 *   <li>{@code leadTimeDays} — срок выполнения (дни);</li>
 *   <li>{@code qualityRating} — рейтинг качества (0…1);</li>
 *   <li>{@code score} — интегральный скор (0…1);</li>
 *   <li>{@code status} — статус варианта;</li>
 *   <li>{@code comment} — комментарий/причина решения;</li>
 *   <li>{@code parameters} — произвольные параметры (JSONB).</li>
 * </ul>
 *
 * <p>Таблица: {@code outsourcing_options} (PostgreSQL, миграция V6).
 */
@Entity
@Table(
        name = "outsourcing_options",
        indexes = {
                @Index(name = "idx_out_gap",         columnList = "gap_id"),
                @Index(name = "idx_out_project",     columnList = "project_id"),
                @Index(name = "idx_out_type",        columnList = "type"),
                @Index(name = "idx_out_status",      columnList = "status"),
                @Index(name = "idx_out_contractor",  columnList = "contractor"),
                @Index(name = "idx_out_score",       columnList = "score"),
                @Index(name = "idx_out_created_at",  columnList = "created_at DESC")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutsourcingOption {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Идентификатор дефицита, к которому относится вариант. */
    @Column(name = "gap_id", nullable = false, length = 64)
    private String gapId;

    /** Идентификатор проекта. */
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    /** Идентификатор gap-анализа, в рамках которого сформирован вариант. */
    @Column(name = "analysis_id", length = 64)
    private String analysisId;

    // ------------------------------------------------------------------
    // Вид аутсорса и подрядчик
    // ------------------------------------------------------------------

    /** Вид аутсорса: CONTRACTOR, PARTNER_PLANT, RENTAL, COOPERATION. */
    @NotBlank
    @Size(max = 32)
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** Наименование подрядчика / партнёра / арендодателя. */
    @Size(max = 255)
    @Column(name = "contractor", length = 255)
    private String contractor;

    /** Контакт подрядчика (телефон, email). */
    @Size(max = 255)
    @Column(name = "contractor_contact", length = 255)
    private String contractorContact;

    /** Регион подрядчика. */
    @Size(max = 64)
    @Column(name = "contractor_region", length = 64)
    private String contractorRegion;

    /** Операция, передаваемая на аутсорс. */
    @Size(max = 128)
    @Column(name = "operation", length = 128)
    private String operation;

    // ------------------------------------------------------------------
    // Коммерческие параметры
    // ------------------------------------------------------------------

    /** Стоимость (руб.). */
    @Column(name = "cost", precision = 14, scale = 2)
    private BigDecimal cost;

    /** Срок выполнения (дни). */
    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    /** Рейтинг качества (0…1). */
    @Column(name = "quality_rating", precision = 8, scale = 4)
    private BigDecimal qualityRating;

    /** Интегральный скор (0…1). */
    @Column(name = "score", precision = 8, scale = 4)
    private BigDecimal score;

    // ------------------------------------------------------------------
    // Статус
    // ------------------------------------------------------------------

    /** Статус: PENDING, SELECTED, REJECTED. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = OutsourcingStatus.PENDING.name();

    /** Комментарий/причина решения. */
    @Size(max = 1000)
    @Column(name = "comment", length = 1000)
    private String comment;

    /** Кто выбрал вариант. */
    @Size(max = 128)
    @Column(name = "selected_by", length = 128)
    private String selectedBy;

    /** Когда выбран. */
    @Column(name = "selected_at")
    private Instant selectedAt;

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

    protected OutsourcingOption() {
    }

    public OutsourcingOption(String id, String gapId, String projectId,
                              String type, String contractor) {
        this.id = id;
        this.gapId = gapId;
        this.projectId = projectId;
        this.type = type;
        this.contractor = contractor;
        this.status = OutsourcingStatus.PENDING.name();
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
        private String gapId;
        private String projectId;
        private String analysisId;
        private String type;
        private String contractor;
        private String contractorContact;
        private String contractorRegion;
        private String operation;
        private BigDecimal cost;
        private Integer leadTimeDays;
        private BigDecimal qualityRating;
        private BigDecimal score;
        private String status;
        private String comment;
        private String selectedBy;
        private Instant selectedAt;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder gapId(String v) { this.gapId = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder analysisId(String v) { this.analysisId = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder type(OutsourcingType v) { this.type = v == null ? null : v.name(); return this; }
        public Builder contractor(String v) { this.contractor = v; return this; }
        public Builder contractorContact(String v) { this.contractorContact = v; return this; }
        public Builder contractorRegion(String v) { this.contractorRegion = v; return this; }
        public Builder operation(String v) { this.operation = v; return this; }
        public Builder cost(BigDecimal v) { this.cost = v; return this; }
        public Builder leadTimeDays(Integer v) { this.leadTimeDays = v; return this; }
        public Builder qualityRating(BigDecimal v) { this.qualityRating = v; return this; }
        public Builder score(BigDecimal v) { this.score = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder status(OutsourcingStatus v) { this.status = v == null ? null : v.name(); return this; }
        public Builder comment(String v) { this.comment = v; return this; }
        public Builder selectedBy(String v) { this.selectedBy = v; return this; }
        public Builder selectedAt(Instant v) { this.selectedAt = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public OutsourcingOption build() {
            OutsourcingOption o = new OutsourcingOption();
            o.id = this.id;
            o.gapId = this.gapId;
            o.projectId = this.projectId;
            o.analysisId = this.analysisId;
            o.type = this.type;
            o.contractor = this.contractor;
            o.contractorContact = this.contractorContact;
            o.contractorRegion = this.contractorRegion;
            o.operation = this.operation;
            o.cost = this.cost;
            o.leadTimeDays = this.leadTimeDays;
            o.qualityRating = this.qualityRating;
            o.score = this.score;
            o.status = this.status == null ? OutsourcingStatus.PENDING.name() : this.status;
            o.comment = this.comment;
            o.selectedBy = this.selectedBy;
            o.selectedAt = this.selectedAt;
            o.parameters = this.parameters;
            Instant now = Instant.now();
            o.createdAt = this.createdAt == null ? now : this.createdAt;
            o.updatedAt = this.updatedAt == null ? now : this.updatedAt;
            return o;
        }
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /** Выбрать вариант. */
    public void select(String selectedBy, String comment) {
        this.status = OutsourcingStatus.SELECTED.name();
        this.selectedBy = selectedBy;
        this.selectedAt = Instant.now();
        this.comment = comment;
        touch();
    }

    /** Отклонить вариант. */
    public void reject(String reason) {
        this.status = OutsourcingStatus.REJECTED.name();
        this.comment = reason;
        touch();
    }

    /** Ожидает ли вариант решения. */
    public boolean isPending() {
        return OutsourcingStatus.PENDING.name().equals(this.status);
    }

    /** Выбран ли вариант. */
    public boolean isSelected() {
        return OutsourcingStatus.SELECTED.name().equals(this.status);
    }

    /** Отклонён ли вариант. */
    public boolean isRejected() {
        return OutsourcingStatus.REJECTED.name().equals(this.status);
    }

    /** Является ли вариант подрядчиком. */
    public boolean isContractor() {
        return OutsourcingType.CONTRACTOR.name().equals(this.type);
    }

    /** Является ли вариант заводом-партнёром. */
    public boolean isPartnerPlant() {
        return OutsourcingType.PARTNER_PLANT.name().equals(this.type);
    }

    /** Является ли вариант арендой. */
    public boolean isRental() {
        return OutsourcingType.RENTAL.name().equals(this.type);
    }

    /** Является ли вариант кооперацией. */
    public boolean isCooperation() {
        return OutsourcingType.COOPERATION.name().equals(this.type);
    }

    /** Есть ли контакт подрядчика. */
    public boolean hasContact() {
        return contractorContact != null && !contractorContact.isBlank();
    }

    /** Получить тип как enum. */
    public OutsourcingType getTypeEnum() {
        try {
            return OutsourcingType.valueOf(this.type);
        } catch (Exception e) {
            return OutsourcingType.CONTRACTOR;
        }
    }

    /** Получить статус как enum. */
    public OutsourcingStatus getStatusEnum() {
        try {
            return OutsourcingStatus.valueOf(this.status);
        } catch (Exception e) {
            return OutsourcingStatus.PENDING;
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

    public String getGapId() { return gapId; }
    public void setGapId(String gapId) { this.gapId = gapId; touch(); }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; touch(); }

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; touch(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; touch(); }

    public String getContractor() { return contractor; }
    public void setContractor(String contractor) { this.contractor = contractor; touch(); }

    public String getContractorContact() { return contractorContact; }
    public void setContractorContact(String contractorContact) { this.contractorContact = contractorContact; touch(); }

    public String getContractorRegion() { return contractorRegion; }
    public void setContractorRegion(String contractorRegion) { this.contractorRegion = contractorRegion; touch(); }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; touch(); }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; touch(); }

    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; touch(); }

    public BigDecimal getQualityRating() { return qualityRating; }
    public void setQualityRating(BigDecimal qualityRating) { this.qualityRating = qualityRating; touch(); }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; touch(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; touch(); }

    public String getSelectedBy() { return selectedBy; }
    public void setSelectedBy(String selectedBy) { this.selectedBy = selectedBy; touch(); }

    public Instant getSelectedAt() { return selectedAt; }
    public void setSelectedAt(Instant selectedAt) { this.selectedAt = selectedAt; touch(); }

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
        if (!(o instanceof OutsourcingOption other)) {
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
        return "OutsourcingOption{" +
                "id='" + id + '\'' +
                ", gapId='" + gapId + '\'' +
                ", projectId='" + projectId + '\'' +
                ", type='" + type + '\'' +
                ", contractor='" + contractor + '\'' +
                ", cost=" + cost +
                ", leadTimeDays=" + leadTimeDays +
                ", status='" + status + '\'' +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенные enum'ы
    // ------------------------------------------------------------------

    /**
     * Вид аутсорса.
     */
    public enum OutsourcingType {
        /** Передача операции стороннему подрядчику. */
        CONTRACTOR,
        /** Передача на завод-партнёр по договору кооперации. */
        PARTNER_PLANT,
        /** Аренда оборудования с оператором. */
        RENTAL,
        /** Кооперация с другим предприятием. */
        COOPERATION
    }

    /**
     * Статус варианта аутсорса.
     */
    public enum OutsourcingStatus {
        /** Предложен, ожидает решения. */
        PENDING,
        /** Выбран пользователем. */
        SELECTED,
        /** Отклонён. */
        REJECTED
    }
}