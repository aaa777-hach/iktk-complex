package ru.iktk.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Доменная модель технологического процесса (ТП) ИКТК.
 *
 * <p>ТП описывает последовательность изготовления объекта продукции —
 * детали или сборочной единицы. В рамках одного проекта может быть
 * несколько ТП: для каждой детали сборки и отдельно для сборки.
 *
 * <p>Ключевые атрибуты:
 * <ul>
 *   <li>тип ТП (литьё, штамповка, механообработка, сборка, ...);</li>
 *   <li>привязка к проекту и к объекту изготовления;</li>
 *   <li>упорядоченный список этапов ({@link Stage});</li>
 *   <li>используемое оборудование и оснастка (ссылки на ID);</li>
 *   <li>версия, автор, временные метки.</li>
 * </ul>
 *
 * <p>Таблица: {@code technology_processes} (PostgreSQL).
 */
@Entity
@Table(
        name = "technology_processes",
        indexes = {
                @Index(name = "idx_tp_project",     columnList = "project_id"),
                @Index(name = "idx_tp_object",      columnList = "object_ref"),
                @Index(name = "idx_tp_type",        columnList = "process_type"),
                @Index(name = "idx_tp_version",     columnList = "version")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TechnologyProcess {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор ТП (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Обозначение ТП (например, «ТП-001/МХ»). */
    @NotBlank
    @Size(max = 128)
    @Column(name = "designation", nullable = false, length = 128)
    private String designation;

    /** Наименование ТП. */
    @Size(max = 255)
    @Column(name = "name", length = 255)
    private String name;

    /** Версия ТП (для версионности и отката). */
    @Size(max = 64)
    @Column(name = "version", length = 64)
    private String version;

    // ------------------------------------------------------------------
    // Привязки
    // ------------------------------------------------------------------

    /** Проект, к которому относится ТП. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * Ссылка на объект изготовления — деталь или сборку.
     * Для детали — идентификатор {@code geometry_models} или {@code assembly_units};
     * для сборки — идентификатор {@code assembly_units}.
     */
    @Column(name = "object_ref", nullable = false, length = 128)
    private String objectRef;

    /** Тип объекта изготовления (деталь или сборка). */
    @Column(name = "object_type", nullable = false, length = 32)
    private Project.ObjectType objectType;

    // ------------------------------------------------------------------
    // Классификация ТП
    // ------------------------------------------------------------------

    /** Тип технологического процесса. */
    @Column(name = "process_type", nullable = false, length = 32)
    private ProcessType processType;

    /** Статус ТП. */
    @Column(name = "status", nullable = false, length = 32)
    private ProcessStatus status;

    /** Вид производства. */
    @Column(name = "production_type", length = 32)
    private ProductionType productionType;

    // ------------------------------------------------------------------
    // Этапы ТП
    // ------------------------------------------------------------------

    /** Упорядоченный список этапов ТП. */
    @OneToMany(
            mappedBy = "technologyProcess",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderColumn(name = "stage_order")
    private List<Stage> stages = new ArrayList<>();

    // ------------------------------------------------------------------
    // Ответственные и временные метки
    // ------------------------------------------------------------------

    /** Автор ТП. */
    @Size(max = 128)
    @Column(name = "author", length = 128)
    private String author;

    /** Момент создания. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    protected TechnologyProcess() {
    }

    public TechnologyProcess(String id,
                             String designation,
                             Project project,
                             String objectRef,
                             Project.ObjectType objectType,
                             ProcessType processType) {
        this.id = id;
        this.designation = designation;
        this.project = project;
        this.objectRef = objectRef;
        this.objectType = objectType;
        this.processType = processType;
        this.status = ProcessStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Добавить этап в конец ТП.
     */
    public void addStage(Stage stage) {
        stage.setTechnologyProcess(this);
        stages.add(stage);
        touch();
    }

    /**
     * Удалить этап из ТП.
     */
    public void removeStage(Stage stage) {
        stages.remove(stage);
        stage.setTechnologyProcess(null);
        touch();
    }

    /**
     * Вставить этап в указанную позицию.
     */
    public void insertStage(int index, Stage stage) {
        stage.setTechnologyProcess(this);
        stages.add(index, stage);
        touch();
    }

    /**
     * Пометить ТП как согласованный.
     */
    public void approve() {
        this.status = ProcessStatus.APPROVED;
        touch();
    }

    /**
     * Пометить ТП как отклонённый.
     */
    public void reject() {
        this.status = ProcessStatus.REJECTED;
        touch();
    }

    /**
     * Пометить ТП как архивный.
     */
    public void archive() {
        this.status = ProcessStatus.ARCHIVED;
        touch();
    }

    /**
     * Число этапов в ТП.
     */
    public int stageCount() {
        return stages.size();
    }

    /**
     * Проверка: является ли ТП сборочным.
     */
    public boolean isAssemblyProcess() {
        return processType == ProcessType.ASSEMBLY;
    }

    /**
     * Проверка: является ли ТП механообработкой.
     */
    public boolean isMachiningProcess() {
        return processType == ProcessType.MACHINING;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        touch();
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
        touch();
    }

    public String getObjectRef() {
        return objectRef;
    }

    public void setObjectRef(String objectRef) {
        this.objectRef = objectRef;
        touch();
    }

    public Project.ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(Project.ObjectType objectType) {
        this.objectType = objectType;
        touch();
    }

    public ProcessType getProcessType() {
        return processType;
    }

    public void setProcessType(ProcessType processType) {
        this.processType = processType;
        touch();
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessStatus status) {
        this.status = status;
        touch();
    }

    public ProductionType getProductionType() {
        return productionType;
    }

    public void setProductionType(ProductionType productionType) {
        this.productionType = productionType;
        touch();
    }

    public List<Stage> getStages() {
        return stages;
    }

    public void setStages(List<Stage> stages) {
        this.stages = stages;
        touch();
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
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
        if (!(o instanceof TechnologyProcess other)) {
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
        return "TechnologyProcess{" +
                "id='" + id + '\'' +
                ", designation='" + designation + '\'' +
                ", processType=" + processType +
                ", objectType=" + objectType +
                ", status=" + status +
                ", stages=" + stages.size() +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенные enum'ы
    // ------------------------------------------------------------------

    /**
     * Тип технологического процесса.
     */
    public enum ProcessType {
        /** Литьё. */
        CASTING,
        /** Объёмная штамповка. */
        FORGING,
        /** Механообработка (включая ЧПУ). */
        MACHINING,
        /** Термическая обработка. */
        HEAT_TREATMENT,
        /** Нанесение покрытий. */
        COATING,
        /** Сборка. */
        ASSEMBLY,
        /** Технический контроль. */
        TECHNICAL_CONTROL,
        /** Нормирование (вспомогательный ТП). */
        NORMING,
        /** Прочие вспомогательные ТП. */
        AUXILIARY
    }

    /**
     * Статус ТП.
     */
    public enum ProcessStatus {
        /** Черновик — ТП создан, но не согласован. */
        DRAFT,
        /** Согласован — ТП утверждён технологом/руководителем. */
        APPROVED,
        /** Отклонён — требуется доработка. */
        REJECTED,
        /** Архивный — ТП закрыт и перемещён в архив. */
        ARCHIVED
    }

    /**
     * Вид производства.
     */
    public enum ProductionType {
        /** Единичное. */
        SINGLE,
        /** Серийное. */
        SERIAL,
        /** Массовое. */
        MASS
    }
}