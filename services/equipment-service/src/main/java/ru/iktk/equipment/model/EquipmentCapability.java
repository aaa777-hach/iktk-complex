package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Доменная модель возможности оборудования ИКТК.
 *
 * <p>Описывает, какие операции может выполнять конкретная единица
 * оборудования. Каждая запись соответствует одному типу операции
 * (например, «точение», «фрезерование», «горячая штамповка»).
 *
 * <p><b>Ключевые атрибуты:</b>
 * <ul>
 *   <li>{@code operationType} — тип операции;</li>
 *   <li>{@code tolerance} — достижимая точность (например, IT7, ±0.01 мм);</li>
 *   <li>{@code maxDimensions} — максимальные габариты заготовки;</li>
 *   <li>{@code minDimensions} — минимальные габариты;</li>
 *   <li>{@code materials} — поддерживаемые материалы;</li>
 *   <li>{@code powerKw} — мощность (кВт);</li>
 *   <li>{@code forceKn} — максимальное усилие (кН);</li>
 *   <li>{@code temperatureC} — рабочая температура (°C);</li>
 *   <li>{@code spindleSpeedRpm} — диапазон оборотов шпинделя;</li>
 *   <li>{@code feedRange} — диапазон подач;</li>
 *   <li>{@code cncCapable} — поддержка ЧПУ;</li>
 *   <li>{@code axesCount} — число управляемых осей (для ЧПУ).</li>
 * </ul>
 *
 * <p><b>Использование:</b>
 * <ul>
 *   <li>matching — сопоставление требований ТП с возможностями;</li>
 *   <li>gap-анализ — выявление дефицита по параметрам;</li>
 *   <li>проверка доступности метода обработки.</li>
 * </ul>
 *
 * <p>Таблица: {@code equipment_capabilities} (PostgreSQL, миграция V4).
 */
@Entity
@Table(
        name = "equipment_capabilities",
        indexes = {
                @Index(name = "idx_cap_equipment",      columnList = "equipment_id"),
                @Index(name = "idx_cap_operation",      columnList = "operation_type"),
                @Index(name = "idx_cap_cnc",            columnList = "cnc_capable"),
                @Index(name = "idx_cap_unique",         columnList = "equipment_id, operation_type",
                        unique = true)
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentCapability {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /**
     * Идентификатор оборудования.
     *
     * <p>Используется и как внешний ключ, и как поле для связи
     * через {@code mappedBy} в {@link Equipment}.
     */
    @Column(name = "equipment_id", nullable = false, length = 64)
    private String equipmentId;

    /**
     * Ссылка на оборудование (для навигации).
     * Загружается лениво, не сериализуется.
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", insertable = false, updatable = false)
    private Equipment equipment;

    // ------------------------------------------------------------------
    // Тип операции
    // ------------------------------------------------------------------

    /** Тип операции (например, TURNING, MILLING, HOT_FORGING, ...). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    /** Наименование операции (человекочитаемое). */
    @Size(max = 255)
    @Column(name = "operation_name", length = 255)
    private String operationName;

    /** Категория операции (основная, вспомогательная, контрольная). */
    @Size(max = 32)
    @Column(name = "operation_category", length = 32)
    private String operationCategory;

    // ------------------------------------------------------------------
    // Точность и габариты
    // ------------------------------------------------------------------

    /** Достижимая точность (например, IT7, ±0.01 мм). */
    @Size(max = 32)
    @Column(name = "tolerance", length = 32)
    private String tolerance;

    /** Класс точности (числовой, IT-квалитет). */
    @Column(name = "accuracy_grade")
    private Integer accuracyGrade;

    /** Максимальные габариты заготовки (например, «1000x500x400»). */
    @Size(max = 64)
    @Column(name = "max_dimensions", length = 64)
    private String maxDimensions;

    /** Минимальные габариты заготовки. */
    @Size(max = 64)
    @Column(name = "min_dimensions", length = 64)
    private String minDimensions;

    /** Максимальный диаметр (мм). */
    @Column(name = "max_diameter_mm", precision = 12, scale = 3)
    private BigDecimal maxDiameterMm;

    /** Максимальная длина (мм). */
    @Column(name = "max_length_mm", precision = 12, scale = 3)
    private BigDecimal maxLengthMm;

    /** Максимальная масса заготовки (кг). */
    @Column(name = "max_mass_kg", precision = 12, scale = 3)
    private BigDecimal maxMassKg;

    // ------------------------------------------------------------------
    // Технологические параметры
    // ------------------------------------------------------------------

    /** Мощность (кВт). */
    @Column(name = "power_kw", precision = 10, scale = 3)
    private BigDecimal powerKw;

    /** Максимальное усилие (кН). */
    @Column(name = "force_kn", precision = 12, scale = 3)
    private BigDecimal forceKn;

    /** Рабочая температура (°C). */
    @Column(name = "temperature_c", precision = 8, scale = 2)
    private BigDecimal temperatureC;

    /** Минимальные обороты шпинделя (об/мин). */
    @Column(name = "spindle_speed_min_rpm")
    private Integer spindleSpeedMinRpm;

    /** Максимальные обороты шпинделя (об/мин). */
    @Column(name = "spindle_speed_max_rpm")
    private Integer spindleSpeedMaxRpm;

    /** Минимальная подача (мм/об). */
    @Column(name = "feed_min", precision = 10, scale = 3)
    private BigDecimal feedMin;

    /** Максимальная подача (мм/об). */
    @Column(name = "feed_max", precision = 10, scale = 3)
    private BigDecimal feedMax;

    /** Производительность (шт/ч). */
    @Column(name = "productivity_per_hour", precision = 10, scale = 2)
    private BigDecimal productivityPerHour;

    // ------------------------------------------------------------------
    // ЧПУ
    // ------------------------------------------------------------------

    /** Поддерживает ли ЧПУ. */
    @Column(name = "cnc_capable", nullable = false)
    private boolean cncCapable = false;

    /** Число управляемых осей (для ЧПУ). */
    @Column(name = "axes_count")
    private Integer axesCount;

    /** Тип СЧПУ (например, Fanuc, Siemens 840D, Heidenhain). */
    @Size(max = 64)
    @Column(name = "cnc_system", length = 64)
    private String cncSystem;

    // ------------------------------------------------------------------
    // Материалы
    // ------------------------------------------------------------------

    /**
     * Поддерживаемые материалы.
     * Хранятся в отдельной таблице {@code equipment_capability_materials}.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "equipment_capability_materials",
            joinColumns = @JoinColumn(name = "capability_id")
    )
    @Column(name = "material_code", length = 64)
    private Set<String> materials = new HashSet<>();

    // ------------------------------------------------------------------
    // Дополнительно
    // ------------------------------------------------------------------

    /** Произвольные параметры (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    /** Комментарий. */
    @Size(max = 1000)
    @Column(name = "comment", length = 1000)
    private String comment;

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

    protected EquipmentCapability() {
    }

    public EquipmentCapability(String id, String equipmentId, String operationType) {
        this.id = id;
        this.equipmentId = equipmentId;
        this.operationType = operationType;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Поддерживает ли материал.
     */
    public boolean supportsMaterial(String materialCode) {
        if (materialCode == null || materials == null) {
            return false;
        }
        return materials.stream()
                .anyMatch(m -> m.equalsIgnoreCase(materialCode));
    }

    /**
     * Проверка: попадает ли требуемая мощность в допустимую.
     */
    public boolean supportsPower(BigDecimal requiredPowerKw) {
        if (requiredPowerKw == null) {
            return true;
        }
        return powerKw != null && powerKw.compareTo(requiredPowerKw) >= 0;
    }

    /**
     * Проверка: попадает ли требуемое усилие в допустимое.
     */
    public boolean supportsForce(BigDecimal requiredForceKn) {
        if (requiredForceKn == null) {
            return true;
        }
        return forceKn != null && forceKn.compareTo(requiredForceKn) >= 0;
    }

    /**
     * Проверка: поддерживается ли ЧПУ.
     */
    public boolean isCncCapable() {
        return cncCapable;
    }

    /**
     * Проверка: поддерживается ли операция с ЧПУ.
     */
    public boolean supportsCncOperation() {
        return cncCapable
                && operationType != null
                && (operationType.contains("CNC")
                    || operationType.contains("cnc"));
    }

    /**
     * Является ли операция основной.
     */
    public boolean isMainOperation() {
        return "MAIN".equalsIgnoreCase(operationCategory);
    }

    /**
     * Добавить материал.
     */
    public void addMaterial(String materialCode) {
        if (materialCode == null || materialCode.isBlank()) {
            return;
        }
        if (this.materials == null) {
            this.materials = new HashSet<>();
        }
        this.materials.add(materialCode);
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

    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }

    public Equipment getEquipment() { return equipment; }
    public void setEquipment(Equipment equipment) { this.equipment = equipment; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; touch(); }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; touch(); }

    public String getOperationCategory() { return operationCategory; }
    public void setOperationCategory(String operationCategory) { this.operationCategory = operationCategory; touch(); }

    public String getTolerance() { return tolerance; }
    public void setTolerance(String tolerance) { this.tolerance = tolerance; touch(); }

    public Integer getAccuracyGrade() { return accuracyGrade; }
    public void setAccuracyGrade(Integer accuracyGrade) { this.accuracyGrade = accuracyGrade; touch(); }

    public String getMaxDimensions() { return maxDimensions; }
    public void setMaxDimensions(String maxDimensions) { this.maxDimensions = maxDimensions; touch(); }

    public String getMinDimensions() { return minDimensions; }
    public void setMinDimensions(String minDimensions) { this.minDimensions = minDimensions; touch(); }

    public BigDecimal getMaxDiameterMm() { return maxDiameterMm; }
    public void setMaxDiameterMm(BigDecimal maxDiameterMm) { this.maxDiameterMm = maxDiameterMm; touch(); }

    public BigDecimal getMaxLengthMm() { return maxLengthMm; }
    public void setMaxLengthMm(BigDecimal maxLengthMm) { this.maxLengthMm = maxLengthMm; touch(); }

    public BigDecimal getMaxMassKg() { return maxMassKg; }
    public void setMaxMassKg(BigDecimal maxMassKg) { this.maxMassKg = maxMassKg; touch(); }

    public BigDecimal getPowerKw() { return powerKw; }
    public void setPowerKw(BigDecimal powerKw) { this.powerKw = powerKw; touch(); }

    public BigDecimal getForceKn() { return forceKn; }
    public void setForceKn(BigDecimal forceKn) { this.forceKn = forceKn; touch(); }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; touch(); }

    public Integer getSpindleSpeedMinRpm() { return spindleSpeedMinRpm; }
    public void setSpindleSpeedMinRpm(Integer spindleSpeedMinRpm) { this.spindleSpeedMinRpm = spindleSpeedMinRpm; touch(); }

    public Integer getSpindleSpeedMaxRpm() { return spindleSpeedMaxRpm; }
    public void setSpindleSpeedMaxRpm(Integer spindleSpeedMaxRpm) { this.spindleSpeedMaxRpm = spindleSpeedMaxRpm; touch(); }

    public BigDecimal getFeedMin() { return feedMin; }
    public void setFeedMin(BigDecimal feedMin) { this.feedMin = feedMin; touch(); }

    public BigDecimal getFeedMax() { return feedMax; }
    public void setFeedMax(BigDecimal feedMax) { this.feedMax = feedMax; touch(); }

    public BigDecimal getProductivityPerHour() { return productivityPerHour; }
    public void setProductivityPerHour(BigDecimal productivityPerHour) { this.productivityPerHour = productivityPerHour; touch(); }

    public boolean isCncCapableFlag() { return cncCapable; }
    public void setCncCapable(boolean cncCapable) { this.cncCapable = cncCapable; touch(); }

    public Integer getAxesCount() { return axesCount; }
    public void setAxesCount(Integer axesCount) { this.axesCount = axesCount; touch(); }

    public String getCncSystem() { return cncSystem; }
    public void setCncSystem(String cncSystem) { this.cncSystem = cncSystem; touch(); }

    public Set<String> getMaterials() { return materials; }
    public void setMaterials(Set<String> materials) { this.materials = materials; touch(); }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; touch(); }

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
        if (!(o instanceof EquipmentCapability other)) {
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
        return "EquipmentCapability{" +
                "id='" + id + '\'' +
                ", equipmentId='" + equipmentId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", tolerance='" + tolerance + '\'' +
                ", maxDimensions='" + maxDimensions + '\'' +
                ", cncCapable=" + cncCapable +
                '}';
    }
}