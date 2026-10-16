package ru.iktk.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.iktk.core.domain.Project;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * DTO запроса на создание/запуск проекта ТПП в оркестраторе ИКТК.
 *
 * <p>Используется в {@code OrchestratorController#createProject} и
 * передаётся в {@code OrchestrationService#startProject}.
 *
 * <p>Содержит минимально необходимые данные для инициализации проекта:
 * <ul>
 *   <li>наименование и обозначение проекта;</li>
 *   <li>тип объекта изготовления (деталь/сборка);</li>
 *   <li>инициатор и ответственный;</li>
 *   <li>идентификатор геометрии (если уже загружена);</li>
 *   <li>произвольные атрибуты (payload) для расширений.</li>
 * </ul>
 *
 * <p>DTO неизменяем после создания — все поля задаются через конструктор
 * или builder. Для JPA/DTO-маппинга предусмотрены геттеры.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "OrchestrationRequest",
        description = "Запрос на создание и запуск проекта ТПП")
public class OrchestrationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // Идентификация проекта
    // ------------------------------------------------------------------

    @Schema(description = "Наименование проекта", example = "Корпус редуктора")
    @NotBlank(message = "Не указано имя проекта")
    @Size(max = 255)
    private String projectName;

    @Schema(description = "Обозначение проекта", example = "КР-001")
    @Size(max = 128)
    private String designation;

    @Schema(description = "Описание проекта")
    @Size(max = 2000)
    private String description;

    // ------------------------------------------------------------------
    // Классификация
    // ------------------------------------------------------------------

    @Schema(description = "Тип объекта изготовления",
            example = "ASSEMBLY",
            allowableValues = {"PART", "ASSEMBLY"})
    private Project.ObjectType objectType;

    @Schema(description = "Код предприятия/филиала", example = "PLANT-01")
    @Size(max = 64)
    private String enterpriseCode;

    // ------------------------------------------------------------------
    // Ответственные
    // ------------------------------------------------------------------

    @Schema(description = "Инициатор проекта (логин или ФИО)", example = "ivanov")
    @Size(max = 128)
    private String requestedBy;

    @Schema(description = "Ответственный технолог", example = "petrov")
    @Size(max = 128)
    private String responsibleEngineer;

    // ------------------------------------------------------------------
    // Связанные данные
    // ------------------------------------------------------------------

    @Schema(description = "Идентификатор ранее загруженной геометрии (если есть)",
            example = "geo-12345")
    @Size(max = 64)
    private String geometryId;

    @Schema(description = "Идентификатор сборочной единицы (для сборок)",
            example = "asm-98765")
    @Size(max = 64)
    private String assemblyId;

    @Schema(description = "Список ТП, запланированных к проектированию",
            example = "[\"CASTING\",\"MACHINING\",\"ASSEMBLY\"]")
    private java.util.List<String> plannedProcessTypes;

    // ------------------------------------------------------------------
    // Расширения
    // ------------------------------------------------------------------

    @Schema(description = "Произвольные дополнительные атрибуты")
    private Map<String, Object> payload;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    /** Пустой конструктор для Jackson. */
    public OrchestrationRequest() {
    }

    /**
     * Основной конструктор.
     */
    public OrchestrationRequest(String projectName,
                                String designation,
                                Project.ObjectType objectType,
                                String requestedBy) {
        this.projectName = projectName;
        this.designation = designation;
        this.objectType = objectType;
        this.requestedBy = requestedBy;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder для удобного создания DTO.
     */
    public static class Builder {
        private String projectName;
        private String designation;
        private String description;
        private Project.ObjectType objectType;
        private String enterpriseCode;
        private String requestedBy;
        private String responsibleEngineer;
        private String geometryId;
        private String assemblyId;
        private java.util.List<String> plannedProcessTypes;
        private Map<String, Object> payload;

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder designation(String designation) {
            this.designation = designation;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder objectType(Project.ObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder enterpriseCode(String enterpriseCode) {
            this.enterpriseCode = enterpriseCode;
            return this;
        }

        public Builder requestedBy(String requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }

        public Builder responsibleEngineer(String responsibleEngineer) {
            this.responsibleEngineer = responsibleEngineer;
            return this;
        }

        public Builder geometryId(String geometryId) {
            this.geometryId = geometryId;
            return this;
        }

        public Builder assemblyId(String assemblyId) {
            this.assemblyId = assemblyId;
            return this;
        }

        public Builder plannedProcessTypes(java.util.List<String> plannedProcessTypes) {
            this.plannedProcessTypes = plannedProcessTypes;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public OrchestrationRequest build() {
            OrchestrationRequest r = new OrchestrationRequest();
            r.projectName = this.projectName;
            r.designation = this.designation;
            r.description = this.description;
            r.objectType = this.objectType;
            r.enterpriseCode = this.enterpriseCode;
            r.requestedBy = this.requestedBy;
            r.responsibleEngineer = this.responsibleEngineer;
            r.geometryId = this.geometryId;
            r.assemblyId = this.assemblyId;
            r.plannedProcessTypes = this.plannedProcessTypes;
            r.payload = this.payload;
            return r;
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /** Проверка: объект — сборка. */
    public boolean isAssembly() {
        return objectType == Project.ObjectType.ASSEMBLY;
    }

    /** Проверка: объект — деталь. */
    public boolean isPart() {
        return objectType == Project.ObjectType.PART;
    }

    /** Проверка: указана ли геометрия. */
    public boolean hasGeometry() {
        return geometryId != null && !geometryId.isBlank();
    }

    /** Проверка: указана ли сборка. */
    public boolean hasAssembly() {
        return assemblyId != null && !assemblyId.isBlank();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Project.ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(Project.ObjectType objectType) {
        this.objectType = objectType;
    }

    public String getEnterpriseCode() {
        return enterpriseCode;
    }

    public void setEnterpriseCode(String enterpriseCode) {
        this.enterpriseCode = enterpriseCode;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public String getResponsibleEngineer() {
        return responsibleEngineer;
    }

    public void setResponsibleEngineer(String responsibleEngineer) {
        this.responsibleEngineer = responsibleEngineer;
    }

    public String getGeometryId() {
        return geometryId;
    }

    public void setGeometryId(String geometryId) {
        this.geometryId = geometryId;
    }

    public String getAssemblyId() {
        return assemblyId;
    }

    public void setAssemblyId(String assemblyId) {
        this.assemblyId = assemblyId;
    }

    public java.util.List<String> getPlannedProcessTypes() {
        return plannedProcessTypes;
    }

    public void setPlannedProcessTypes(java.util.List<String> plannedProcessTypes) {
        this.plannedProcessTypes = plannedProcessTypes;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrchestrationRequest other)) {
            return false;
        }
        return Objects.equals(projectName, other.projectName)
                && Objects.equals(designation, other.designation)
                && objectType == other.objectType
                && Objects.equals(requestedBy, other.requestedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, designation, objectType, requestedBy);
    }

    @Override
    public String toString() {
        return "OrchestrationRequest{" +
                "projectName='" + projectName + '\'' +
                ", designation='" + designation + '\'' +
                ", objectType=" + objectType +
                ", requestedBy='" + requestedBy + '\'' +
                ", geometryId='" + geometryId + '\'' +
                ", assemblyId='" + assemblyId + '\'' +
                '}';
    }
}