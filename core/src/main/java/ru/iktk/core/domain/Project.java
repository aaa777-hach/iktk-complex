package ru.iktk.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Objects;

/**
 * Доменная модель проекта технологической подготовки производства (ТПП).
 *
 * <p>Проект — корневая сущность ИКТК. Он объединяет:
 * <ul>
 *   <li>геометрию объекта изготовления (деталь или сборочная единица);</li>
 *   <li>дерево состава изделия (BOM) для сборок;</li>
 *   <li>спроектированные технологические процессы и оснастку;</li>
 *   <li>требования к оборудованию, gap-анализ и рекомендации;</li>
 *   <li>нормы, комплект документации и результаты техконтроля.</li>
 * </ul>
 *
 * <p>Сущность намеренно «тонкая»: она не хранит runtime-состояние конечного
 * автомата (это делает {@code ProjectRuntime} в оркестраторе), а содержит
 * бизнес-атрибуты проекта, общие для всех модулей комплекса.
 *
 * <p>Таблица: {@code projects} (PostgreSQL).
 */
@Entity
@Table(
        name = "projects",
        indexes = {
                @Index(name = "idx_projects_designation", columnList = "designation"),
                @Index(name = "idx_projects_status",      columnList = "status"),
                @Index(name = "idx_projects_created_at",  columnList = "created_at")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Project {

    // ------------------------------------------------------------------
    // Идентификация
    // ------------------------------------------------------------------

    /** Уникальный идентификатор проекта (UUID). */
    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    /** Обозначение проекта (например, «Корпус-001»). */
    @NotBlank
    @Size(max = 128)
    @Column(name = "designation", nullable = false, length = 128)
    private String designation;

    /** Наименование проекта. */
    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Описание проекта (назначение, особенности). */
    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    // ------------------------------------------------------------------
    // Классификация
    // ------------------------------------------------------------------

    /**
     * Тип объекта изготовления.
     * {@link ObjectType#PART} — деталь; {@link ObjectType#ASSEMBLY} — сборочная единица.
     */
    @Column(name = "object_type", nullable = false, length = 32)
    private ObjectType objectType;

    /**
     * Статус проекта в бизнес-смысле (не путать с runtime-состоянием автомата).
     */
    @Column(name = "status", nullable = false, length = 32)
    private ProjectStatus status;

    /** Код предприятия / филиала (для мультизаводских сценариев). */
    @Size(max = 64)
    @Column(name = "enterprise_code", length = 64)
    private String enterpriseCode;

    // ------------------------------------------------------------------
    // Ответственные и временные метки
    // ------------------------------------------------------------------

    /** Автор/инициатор проекта. */
    @Size(max = 128)
    @Column(name = "requested_by", length = 128)
    private String requestedBy;

    /** Ответственный технолог. */
    @Size(max = 128)
    @Column(name = "responsible_engineer", length = 128)
    private String responsibleEngineer;

    /** Момент создания проекта. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Момент последнего изменения. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    /** Конструктор по умолчанию для JPA. */
    protected Project() {
    }

    public Project(String id,
                   String designation,
                   String name,
                   ObjectType objectType) {
        this.id = id;
        this.designation = designation;
        this.name = name;
        this.objectType = objectType;
        this.status = ProjectStatus.DRAFT;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Бизнес-методы
    // ------------------------------------------------------------------

    /**
     * Пометить проект как активный (после успешной инициализации).
     */
    public void activate() {
        this.status = ProjectStatus.ACTIVE;
        touch();
    }

    /**
     * Пометить проект как завершённый.
     */
    public void complete() {
        this.status = ProjectStatus.COMPLETED;
        touch();
    }

    /**
     * Пометить проект как архивированный.
     */
    public void archive() {
        this.status = ProjectStatus.ARCHIVED;
        touch();
    }

    /**
     * Пометить проект как отклонённый (например, техконтролем).
     */
    public void reject() {
        this.status = ProjectStatus.REJECTED;
        touch();
    }

    /**
     * Является ли объект сборочной единицей.
     */
    public boolean isAssembly() {
        return objectType == ObjectType.ASSEMBLY;
    }

    /**
     * Является ли объект деталью.
     */
    public boolean isPart() {
        return objectType == ObjectType.PART;
    }

    /** Обновить метку времени. */
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

    public ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(ObjectType objectType) {
        this.objectType = objectType;
        touch();
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
        touch();
    }

    public String getEnterpriseCode() {
        return enterpriseCode;
    }

    public void setEnterpriseCode(String enterpriseCode) {
        this.enterpriseCode = enterpriseCode;
        touch();
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
        touch();
    }

    public String getResponsibleEngineer() {
        return responsibleEngineer;
    }

    public void setResponsibleEngineer(String responsibleEngineer) {
        this.responsibleEngineer = responsibleEngineer;
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
        if (!(o instanceof Project other)) {
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
        return "Project{" +
                "id='" + id + '\'' +
                ", designation='" + designation + '\'' +
                ", name='" + name + '\'' +
                ", objectType=" + objectType +
                ", status=" + status +
                '}';
    }

    // ------------------------------------------------------------------
    // Вложенные enum'ы
    // ------------------------------------------------------------------

    /**
     * Тип объекта изготовления.
     */
    public enum ObjectType {
        /** Деталь — единый объект без входящих компонентов. */
        PART,
        /** Сборочная единица — объект, состоящий из деталей и подсборок. */
        ASSEMBLY
    }

    /**
     * Бизнес-статус проекта.
     */
    public enum ProjectStatus {
        /** Черновик — проект создан, но не запущен. */
        DRAFT,
        /** Активный — идёт проектирование ТП. */
        ACTIVE,
        /** Завершён — все этапы пройдены, документация сформирована. */
        COMPLETED,
        /** Отклонён — техконтроль или руководитель вернули на доработку. */
        REJECTED,
        /** Архивирован — проект закрыт и перемещён в архив. */
        ARCHIVED
    }
}