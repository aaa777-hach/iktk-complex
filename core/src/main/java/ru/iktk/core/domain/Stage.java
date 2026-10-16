package ru.iktk.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Доменная модель этапа (операции) технологического процесса ИКТК.
 *
 * <p>Этап — минимальная структурная единица ТП. Он описывает одну операцию
 * или переход, выполняемый над объектом изготовления:
 * <ul>
 *   <li>наименование и обозначение операции (например, «Токарная 010»);</li>
 *   <li>тип операции ({@link OperationType});</li>
 *   <li>требуемое оборудование и оснастку;</li>
 *   <li>нормы времени и разряд работы;</li>
 *   <li>последовательный номер внутри ТП.</li>
 * </ul>
 *
 * <p>Этапы связаны с {@link TechnologyProcess} отношением «многие к одному»
 * и упорядочены через {@code order}. Оборудование и оснастка хранятся как
 * ссылки (ID) — сами сущности находятся в сервисах {@code equipment-service}
 * и {@code assembly-process-service}.
 *
 * <p>Таблица: {@code stages} (PostgreSQL).
 */
@Entity
@Table(
        name = "stages",
        indexes = {
                @Index(name = "idx_stages_tp",          columnList = "technology_process_id"),
                @Index(name = "idx_stages_operation",   columnList = "operation_type"),
                @Index(name = "idx_stages_equipment",   columnList = "equipment_id"),
                @Index(name = "idx_stages_tooling",     columnList = "tooling_id")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Stage {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор этапа (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Обозначение этапа (например, «010»). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "designation", nullable = false, length = 64)
    private String designation;

    /** Наименование этапа (например, «Токарная»). */
    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Описание этапа (что делается, чем, зачем). */
    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    // ------------------------------------------------------------------
    // Привязка к ТП
    // ------------------------------------------------------------------

    /** Технологический процесс, к которому относится этап. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technology_process_id", nullable = false)
    private TechnologyProcess technologyProcess;

    /** Порядковый номер этапа внутри ТП (начиная с 1). */
    @Column(name = "stage_order", nullable = false)
    private int order;

    // ------------------------------------------------------------------
    // Классификация
    // ------------------------------------------------------------------

    /** Тип операции. */
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType;

    /** Вид этапа (основной, вспомогательный, контрольный). */
    @Column(name = "stage_kind", nullable = false, length = 32)
    private StageKind stageKind;

    /** Разряд работы (1–8). */
    @Column(name = "work_grade")
    private Integer workGrade;

    // ------------------------------------------------------------------
    // Ресурсы
    // ------------------------------------------------------------------

    /** Идентификатор требуемого оборудования (ссылка на equipment-service). */
    @Size(max = 64)
    @Column(name = "equipment_id", length = 64)
    private String equipmentId;

    /** Идентификатор оснастки (ссылка на assembly-process-service / tooling). */
    @Size(max = 64)
    @Column(name = "tooling_id", length = 64)
    private String toolingId;

    /** Идентификатор программы ЧПУ (если применимо). */
    @Size(max = 64)
    @Column(name = "cnc_program_id", length = 64)
    private String cncProgramId;

    // ------------------------------------------------------------------
    // Нормы
    // ------------------------------------------------------------------

    /** Норма времени (штучное время, мин). */
    @Column(name = "time_norm_min", precision = 12, scale = 3)
    private BigDecimal timeNormMin;

    /** Подготовительно-заключительное время (мин). */
    @Column(name = "setup_time_min", precision = 12, scale = 3)
    private BigDecimal setupTimeMin;

    /** Разряд/тариф (если рассчитывается отдельно). */
    @Size(max = 64)
    @Column(name = "tariff_code", length = 64)
    private String tariffCode;

    // ------------------------------------------------------------------
    // Требования к оборудованию (для gap-анализа)
    // ------------------------------------------------------------------

    /** Требуемая точность (например, IT-квалитет). */
    @Size(max = 32)
    @Column(name = "required_accuracy", length = 32)
    private String requiredAccuracy;

    /** Максимальный габарит обрабатываемой детали (мм). */
    @Size(max = 64)
    @Column(name = "max_dimensions", length = 64)
    private String maxDimensions;

    /** Требуемая мощность (кВт). */
    @Column(name = "required_power_kw", precision = 10, scale = 3)
    private BigDecimal requiredPowerKw;

    /** Материал (для проверки совместимости с оборудованием). */
    @Size(max = 128)
    @Column(name = "material", length = 128)
    private String material;

    // ------------------------------------------------------------------
    // Служебные поля
    // ------------------------------------------------------------------

    /** Комментарий технолога. */
    @Size(max = 1000)
    @Column(name = "comment", length = 1000)
    private String comment;

    /** Момент создания. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    protected Stage() {
    }

    public Stage(String id,
                 String designation,
                 String name,
                 OperationType operationType,
                 int order) {
        this.id = id;
        this.designation = designation;
        this.name = name;
        this.operationType = operationType;
        this.stageKind = StageKind.MAIN;
        this.order = order;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Назначить этапу оборудование.
     */
    public void assignEquipment(String equipmentId) {
        this.equipmentId = equipmentId;
        touch();
    }

    /**
     * Назначить этапу оснастку.
     */
    public void assignTooling(String toolingId) {
        this.toolingId = toolingId;
        touch();
    }

    /**
     * Назначить программу ЧПУ.
     */
    public void assignCncProgram(String cncProgramId) {
        this.cncProgramId = cncProgramId;
        touch();
    }

    /**
     * Установить норму времени.
     */
    public void setTimeNorm(BigDecimal timeNormMin, BigDecimal setupTimeMin) {
        this.timeNormMin = timeNormMin;
        this.setupTimeMin = setupTimeMin;
        touch();
    }

    /**
     * Проверка: есть ли у этапа назначенное оборудование.
     */
    public boolean hasEquipment() {
        return equipmentId != null && !equipmentId.isBlank();
    }

    /**
     * Проверка: есть ли у этапа назначенная оснастка.
     */
    public boolean hasTooling() {
        return toolingId != null && !toolingId.isBlank();
    }

    /**
     * Проверка: требуется ли программа ЧПУ.
     */
    public boolean requiresCncProgram() {
        return operationType == OperationType.CNC_MACHINING
                || operationType == OperationType.MACHINING;
    }

    /**
     * Общая норма времени (штучное + подготовительно-заключительное).
     */
    public BigDecimal totalTimeNorm() {
        BigDecimal a = timeNormMin == null ? BigDecimal.ZERO : timeNormMin;
        BigDecimal b = setupTimeMin == null ? BigDecimal.ZERO : setupTimeMin;
        return a.add(b);
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
        touch();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        touch();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public TechnologyProcess getTechnologyProcess() {
        return technologyProcess;
    }

    public void setTechnologyProcess(TechnologyProcess technologyProcess) {
        this.technologyProcess = technologyProcess;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
        touch();
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
        touch();
    }

    public StageKind getStageKind() {
        return stageKind;
    }

    public void setStageKind(StageKind stageKind) {
        this.stageKind = stageKind;
        touch();
    }

    public Integer getWorkGrade() {
        return workGrade;
    }

    public void setWorkGrade(Integer workGrade) {
        this.workGrade = workGrade;
        touch();
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
        touch();
    }

    public String getToolingId() {
        return toolingId;
    }

    public void setToolingId(String toolingId) {
        this.toolingId = toolingId;
        touch();
    }

    public String getCncProgramId() {
        return cncProgramId;
    }

    public void setCncProgramId(String cncProgramId) {
        this.cncProgramId = cncProgramId;
        touch();
    }

    public BigDecimal getTimeNormMin() {
        return timeNormMin;
    }

    public void setTimeNormMin(BigDecimal timeNormMin) {
        this.timeNormMin = timeNormMin;
        touch();
    }

    public BigDecimal getSetupTimeMin() {
        return setupTimeMin;
    }

    public void setSetupTimeMin(BigDecimal setupTimeMin) {
        this.setupTimeMin = setupTimeMin;
        touch();
    }

    public String getTariffCode() {
        return tariffCode;
    }

    public void setTariffCode(String tariffCode) {
        this.tariffCode = tariffCode;
        touch();
    }

    public String getRequiredAccuracy() {
        return requiredAccuracy;
    }

    public void setRequiredAccuracy(String requiredAccuracy) {
        this.requiredAccuracy = requiredAccuracy;
        touch();
    }

    public String getMaxDimensions() {
        return maxDimensions;
    }

    public void setMaxDimensions(String maxDimensions) {
        this.maxDimensions = maxDimensions;
        touch();
    }

    public BigDecimal getRequiredPowerKw() {
        return requiredPowerKw;
    }

    public void setRequiredPowerKw(BigDecimal requiredPowerKw) {
        this.requiredPowerKw = requiredPowerKw;
        touch();
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
        touch();
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Stage other)) {
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
        return "Stage{" +
                "id='" + id + '\'' +
                ", designation='" + designation + '\'' +
                ", name='" + name + '\'' +
                ", operationType=" + operationType +
                ", order=" + order +
                ", equipmentId='" + equipmentId + '\'' +
                ", toolingId='" + toolingId + '\'' +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенные enum'ы
    // ------------------------------------------------------------------

    /**
     * Тип операции этапа.
     */
    public enum OperationType {
        /** Заготовительная операция. */
        BLANK_PREPARATION,
        /** Литьё. */
        CASTING,
        /** Горячая объёмная штамповка. */
        HOT_FORGING,
        /** Холодная объёмная штамповка. */
        COLD_FORGING,
        /** Листовая штамповка. */
        SHEET_STAMPING,
        /** Механообработка (универсальная). */
        MACHINING,
        /** Механообработка на станках с ЧПУ. */
        CNC_MACHINING,
        /** Термическая обработка. */
        HEAT_TREATMENT,
        /** Нанесение покрытий. */
        COATING,
        /** Сборка. */
        ASSEMBLY,
        /** Технический контроль. */
        TECHNICAL_CONTROL,
        /** Нормирование (вспомогательная операция). */
        NORMING,
        /** Прочие вспомогательные операции. */
        AUXILIARY
    }

    /**
     * Вид этапа в ТП.
     */
    public enum StageKind {
        /** Основная операция. */
        MAIN,
        /** Вспомогательная операция. */
        AUXILIARY,
        /** Контрольная операция. */
        CONTROL,
        /** Транспортная операция. */
        TRANSPORT
    }
}