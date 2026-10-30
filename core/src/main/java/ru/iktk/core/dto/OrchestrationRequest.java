package ru.iktk.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.iktk.core.domain.Project;

import java.io.Serializable;
import java.util.List;
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
 *   <li>информацию о модели (material/volume/mass/complexity/warnings) —
 *       из ответа {@code POST /api/v1/geometry/from-step};</li>
 *   <li>заглушку выбора ТП ({@code stubProcessType}) — на первом этапе;</li>
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
    private List<String> plannedProcessTypes;

    // ------------------------------------------------------------------
    // Заглушка выбора ТП (на первом этапе — без анализа модели)
    // ------------------------------------------------------------------

    /**
     * Заглушка выбора ТП. Если {@code plannedProcessTypes} не задан,
     * оркестратор берёт ТП отсюда (или из конфига
     * {@code iktk.orchestrator.stub-process-type}).
     *
     * <p>Допустимые значения: {@code MACHINING}, {@code CASTING}, {@code FORGING}.
     * Коды заглушки: MACHINING → 1, CASTING → 2, FORGING → 3.
     */
    @Schema(description = "Заглушка выбора ТП (MACHINING/CASTING/FORGING)",
            example = "MACHINING")
    @Size(max = 32)
    private String stubProcessType;

    // ------------------------------------------------------------------
    // Информация о модели (из ответа POST /api/v1/geometry/from-step)
    // ------------------------------------------------------------------

    @Schema(description = "Материал детали (эвристика integration)", example = "Steel 45")
    @Size(max = 128)
    private String material;

    @Schema(description = "Объём модели, мм^3 (эвристика integration)", example = "125000.0")
    private Double volume;

    @Schema(description = "Масса модели, кг (эвристика integration)", example = "0.98")
    private Double mass;

    @Schema(description = "Оценка сложности модели (эвристика integration)", example = "3")
    private Integer complexity;

    @Schema(description = "Предупреждения о достоверности эвристик",
            example = "[\"material not detected: fallback Steel 45\"]")
    private List<String> warnings;

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
        private List<String> plannedProcessTypes;
        private String stubProcessType;
        private String material;
        private Double volume;
        private Double mass;
        private Integer complexity;
        private List<String> warnings;
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

        public Builder plannedProcessTypes(List<String> plannedProcessTypes) {
            this.plannedProcessTypes = plannedProcessTypes;
            return this;
        }

        public Builder stubProcessType(String stubProcessType) {
            this.stubProcessType = stubProcessType;
            return this;
        }

        public Builder material(String material) {
            this.material = material;
            return this;
        }

        public Builder volume(Double volume) {
            this.volume = volume;
            return this;
        }

        public Builder mass(Double mass) {
            this.mass = mass;
            return this;
        }

        public Builder complexity(Integer complexity) {
            this.complexity = complexity;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
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
            r.stubProcessType = this.stubProcessType;
            r.material = this.material;
            r.volume = this.volume;
            r.mass = this.mass;
            r.complexity = this.complexity;
            r.warnings = this.warnings;
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

    /** Проверка: задана ли заглушка ТП. */
    public boolean hasStubProcessType() {
        return stubProcessType != null && !stubProcessType.isBlank();
    }

    /** Проверка: есть ли эвристики модели (material/volume/mass/complexity/warnings). */
    public boolean hasModelInfo() {
        return material != null || volume != null || mass != null
                || complexity != null || (warnings != null && !warnings.isEmpty());
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

    public List<String> getPlannedProcessTypes() {
        return plannedProcessTypes;
    }

    public void setPlannedProcessTypes(List<String> plannedProcessTypes) {
        this.plannedProcessTypes = plannedProcessTypes;
    }

    public String getStubProcessType() {
        return stubProcessType;
    }

    public void setStubProcessType(String stubProcessType) {
        this.stubProcessType = stubProcessType;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public Double getMass() {
        return mass;
    }

    public void setMass(Double mass) {
        this.mass = mass;
    }

    public Integer getComplexity() {
        return complexity;
    }

    public void setComplexity(Integer complexity) {
        this.complexity = complexity;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
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
                ", stubProcessType='" + stubProcessType + '\'' +
                ", material='" + material + '\'' +
                '}';
    }
}