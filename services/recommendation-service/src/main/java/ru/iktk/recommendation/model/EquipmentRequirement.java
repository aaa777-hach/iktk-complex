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
 * Доменная модель требования к оборудованию ИКТК.
 *
 * <p>Описывает требование, вытекающее из операции спроектированного ТП
 * (литьё, штамповка, механообработка, сборка). Используется для
 * сопоставления с парком предприятия и выявления дефицита.
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code projectId} — проект;</li>
 *   <li>{@code processId} — ТП, из которого вытекает требование;</li>
 *   <li>{@code stageId} — этап ТП;</li>
 *   <li>{@code operationId} — операция (этап) ТП;</li>
 *   <li>{@code operationType} — тип операции (например, TURNING, MILLING,
 *       CNC_MACHINING, CASTING, HOT_FORGING);</li>
 *   <li>{@code requiredEquipmentType} — тип требуемого оборудования;</li>
 *   <li>{@code requiredAccuracy} — требуемая точность (IT7, ±0.01 мм);</li>
 *   <li>{@code maxDimensions} / {@code minDimensions} — габариты;</li>
 *   <li>{@code material} — материал;</li>
 *   <li>{@code requiredPowerKw} — мощность (кВт);</li>
 *   <li>{@code requiredForceKn} — усилие (кН);</li>
 *   <li>{@code requiredCnc} — требуется ли ЧПУ;</li>
 *   <li>{@code requiredAxes} — число управляемых осей;</li>
 *   <li>{@code requiredTemperatureC} — рабочая температура (°C);</li>
 *   <li>{@code quantity} — количество (число операций такого типа);</li>
 *   <li>{@code priority} — приоритет (0 — обычный, 10 — критичный);</li>
 *   <li>{@code analysisId} — ссылка на gap-анализ, в рамках которого
 *       требование обработано (может быть {@code null});</li>
 *   <li>{@code notes} — примечание;</li>
 *   <li>{@code parameters} — произвольные параметры (JSONB).</li>
 * </ul>
 *
 * <p>Таблица: {@code equipment_requirements} (PostgreSQL, миграция V6).
 */
@Entity
@Table(
        name = "equipment_requirements",
        indexes = {
                @Index(name = "idx_req_project",         columnList = "project_id"),
                @Index(name = "idx_req_process",         columnList = "process_id"),
                @Index(name = "idx_req_stage",           columnList = "stage_id"),
                @Index(name = "idx_req_operation",       columnList = "operation_id"),
                @Index(name = "idx_req_analysis",        columnList = "analysis_id"),
                @Index(name = "idx_req_operation_type",  columnList = "operation_type"),
                @Index(name = "idx_req_equipment_type",  columnList = "required_equipment_type"),
                @Index(name = "idx_req_material",        columnList = "material"),
                @Index(name = "idx_req_cnc",             columnList = "required_cnc"),
                @Index(name = "idx_req_priority",        columnList = "priority"),
                @Index(name = "idx_req_created_at",      columnList = "created_at")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentRequirement {

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

    /** Идентификатор технологического процесса. */
    @Column(name = "process_id", length = 64)
    private String processId;

    /** Идентификатор этапа ТП. */
    @Column(name = "stage_id", length = 64)
    private String stageId;

    /** Идентификатор операции. */
    @Column(name = "operation_id", length = 64)
    private String operationId;

    /** Идентификатор gap-анализа, обработавшего это требование. */
    @Column(name = "analysis_id", length = 64)
    private String analysisId;

    // ------------------------------------------------------------------
    // Тип операции и требуемое оборудование
    // ------------------------------------------------------------------

    /** Тип операции (TURNING, MILLING, CNC_MACHINING, CASTING, ...). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    /** Тип требуемого оборудования. */
    @Size(max = 64)
    @Column(name = "required_equipment_type", length = 64)
    private String requiredEquipmentType;

    // ------------------------------------------------------------------
    // Технические параметры
    // ------------------------------------------------------------------

    /** Требуемая точность (IT7, ±0.01 мм). */
    @Size(max = 32)
    @Column(name = "required_accuracy", length = 32)
    private String requiredAccuracy;

    /** Максимальные габариты заготовки (например, «1000x500x400»). */
    @Size(max = 64)
    @Column(name = "max_dimensions", length = 64)
    private String maxDimensions;

    /** Минимальные габариты заготовки. */
    @Size(max = 64)
    @Column(name = "min_dimensions", length = 64)
    private String minDimensions;

    /** Материал (код из справочника). */
    @Size(max = 128)
    @Column(name = "material", length = 128)
    private String material;

    /** Требуемая мощность (кВт). */
    @Column(name = "required_power_kw", precision = 10, scale = 3)
    private BigDecimal requiredPowerKw;

    /** Требуемое усилие (кН). */
    @Column(name = "required_force_kn", precision = 12, scale = 3)
    private BigDecimal requiredForceKn;

    /** Требуется ли поддержка ЧПУ. */
    @Column(name = "required_cnc", nullable = false)
    private boolean requiredCnc = false;

    /** Число управляемых осей (для ЧПУ). */
    @Column(name = "required_axes")
    private Integer requiredAxes;

    /** Требуемая рабочая температура (°C). */
    @Column(name = "required_temperature_c", precision = 8, scale = 2)
    private BigDecimal requiredTemperatureC;

    // ------------------------------------------------------------------
    // Количество и приоритет
    // ------------------------------------------------------------------

    /** Количество операций данного типа. */
    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    /**
     * Приоритет требования:
     * <ul>
     *   <li>0–3 — обычный;</li>
     *   <li>4–7 — важный;</li>
     *   <li>8–10 — критичный.</li>
     * </ul>
     */
    @Column(name = "priority", nullable = false)
    private int priority = 0;

    // ------------------------------------------------------------------
    // Дополнительно
    // ------------------------------------------------------------------

    /** Примечание. */
    @Size(max = 1000)
    @Column(name = "notes", length = 1000)
    private String notes;

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

    protected EquipmentRequirement() {
    }

    public EquipmentRequirement(String id, String projectId, String operationType) {
        this.id = id;
        this.projectId = projectId;
        this.operationType = operationType;
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
        private String processId;
        private String stageId;
        private String operationId;
        private String analysisId;
        private String operationType;
        private String requiredEquipmentType;
        private String requiredAccuracy;
        private String maxDimensions;
        private String minDimensions;
        private String material;
        private BigDecimal requiredPowerKw;
        private BigDecimal requiredForceKn;
        private boolean requiredCnc = false;
        private Integer requiredAxes;
        private BigDecimal requiredTemperatureC;
        private int quantity = 1;
        private int priority = 0;
        private String notes;
        private Map<String, Object> parameters;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder processId(String v) { this.processId = v; return this; }
        public Builder stageId(String v) { this.stageId = v; return this; }
        public Builder operationId(String v) { this.operationId = v; return this; }
        public Builder analysisId(String v) { this.analysisId = v; return this; }
        public Builder operationType(String v) { this.operationType = v; return this; }
        public Builder requiredEquipmentType(String v) { this.requiredEquipmentType = v; return this; }
        public Builder requiredAccuracy(String v) { this.requiredAccuracy = v; return this; }
        public Builder maxDimensions(String v) { this.maxDimensions = v; return this; }
        public Builder minDimensions(String v) { this.minDimensions = v; return this; }
        public Builder material(String v) { this.material = v; return this; }
        public Builder requiredPowerKw(BigDecimal v) { this.requiredPowerKw = v; return this; }
        public Builder requiredForceKn(BigDecimal v) { this.requiredForceKn = v; return this; }
        public Builder requiredCnc(boolean v) { this.requiredCnc = v; return this; }
        public Builder requiredAxes(Integer v) { this.requiredAxes = v; return this; }
        public Builder requiredTemperatureC(BigDecimal v) { this.requiredTemperatureC = v; return this; }
        public Builder quantity(int v) { this.quantity = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }
        public Builder notes(String v) { this.notes = v; return this; }
        public Builder parameters(Map<String, Object> v) { this.parameters = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public EquipmentRequirement build() {
            EquipmentRequirement r = new EquipmentRequirement();
            r.id = this.id;
            r.projectId = this.projectId;
            r.processId = this.processId;
            r.stageId = this.stageId;
            r.operationId = this.operationId;
            r.analysisId = this.analysisId;
            r.operationType = this.operationType;
            r.requiredEquipmentType = this.requiredEquipmentType;
            r.requiredAccuracy = this.requiredAccuracy;
            r.maxDimensions = this.maxDimensions;
            r.minDimensions = this.minDimensions;
            r.material = this.material;
            r.requiredPowerKw = this.requiredPowerKw;
            r.requiredForceKn = this.requiredForceKn;
            r.requiredCnc = this.requiredCnc;
            r.requiredAxes = this.requiredAxes;
            r.requiredTemperatureC = this.requiredTemperatureC;
            r.quantity = this.quantity;
            r.priority = this.priority;
            r.notes = this.notes;
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

    /**
     * Требуется ли ЧПУ.
     */
    public boolean requiresCnc() {
        return requiredCnc || (requiredAxes != null && requiredAxes > 0);
    }

    /**
     * Является ли требование критичным.
     */
    public boolean isCritical() {
        return priority >= 8;
    }

    /**
     * Является ли требование важным.
     */
    public boolean isImportant() {
        return priority >= 4 && priority < 8;
    }

    /**
     * Является ли требование обычным.
     */
    public boolean isNormal() {
        return priority < 4;
    }

    /**
     * Связано ли требование с анализом.
     */
    public boolean isAnalyzed() {
        return analysisId != null && !analysisId.isBlank();
    }

    /**
     * Привязать требование к анализу.
     */
    public void assignToAnalysis(String analysisId) {
        this.analysisId = analysisId;
        touch();
    }

    /**
     * Повысить приоритет.
     */
    public void escalatePriority(int delta) {
        this.priority = Math.min(10, this.priority + Math.max(0, delta));
        touch();
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

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; touch(); }

    public String getStageId() { return stageId; }
    public void setStageId(String stageId) { this.stageId = stageId; touch(); }

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; touch(); }

    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; touch(); }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; touch(); }

    public String getRequiredEquipmentType() { return requiredEquipmentType; }
    public void setRequiredEquipmentType(String requiredEquipmentType) { this.requiredEquipmentType = requiredEquipmentType; touch(); }

    public String getRequiredAccuracy() { return requiredAccuracy; }
    public void setRequiredAccuracy(String requiredAccuracy) { this.requiredAccuracy = requiredAccuracy; touch(); }

    public String getMaxDimensions() { return maxDimensions; }
    public void setMaxDimensions(String maxDimensions) { this.maxDimensions = maxDimensions; touch(); }

    public String getMinDimensions() { return minDimensions; }
    public void setMinDimensions(String minDimensions) { this.minDimensions = minDimensions; touch(); }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; touch(); }

    public BigDecimal getRequiredPowerKw() { return requiredPowerKw; }
    public void setRequiredPowerKw(BigDecimal requiredPowerKw) { this.requiredPowerKw = requiredPowerKw; touch(); }

    public BigDecimal getRequiredForceKn() { return requiredForceKn; }
    public void setRequiredForceKn(BigDecimal requiredForceKn) { this.requiredForceKn = requiredForceKn; touch(); }

    public boolean isRequiredCnc() { return requiredCnc; }
    public void setRequiredCnc(boolean requiredCnc) { this.requiredCnc = requiredCnc; touch(); }

    public Integer getRequiredAxes() { return requiredAxes; }
    public void setRequiredAxes(Integer requiredAxes) { this.requiredAxes = requiredAxes; touch(); }

    public BigDecimal getRequiredTemperatureC() { return requiredTemperatureC; }
    public void setRequiredTemperatureC(BigDecimal requiredTemperatureC) { this.requiredTemperatureC = requiredTemperatureC; touch(); }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; touch(); }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; touch(); }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }

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
        if (!(o instanceof EquipmentRequirement other)) {
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
        return "EquipmentRequirement{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", requiredCnc=" + requiredCnc +
                ", priority=" + priority +
                ", analysisId='" + analysisId + '\'' +
                '}';
    }
}