package ru.iktk.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.iktk.core.domain.Project;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DTO ответа оркестратора ИКТК.
 *
 * <p>Возвращается из {@code OrchestratorController} и {@code OrchestrationService}
 * при:
 * <ul>
 *   <li>создании/запуске проекта — {@code createProject};</li>
 *   <li>продолжении оркестрации — {@code run};</li>
 *   <li>запросе сводки — {@code getSummary};</li>
 *   <li>обработке точек контроля — {@code approve}, {@code sendEvent},
 *       {@code acceptRecommendation}, {@code adjustProcess}, {@code selectOutsourcing};</li>
 *   <li>откате версии — {@code rollback}.</li>
 * </ul>
 *
 * <p>Содержит:
 * <ul>
 *   <li>идентификацию проекта (ID, имя, обозначение);</li>
 *   <li>текущее состояние конечного автомата;</li>
 *   <li>список доступных событий — для UI и точек контроля;</li>
 *   <li>сведения о текущем этапе и прогрессе;</li>
 *   <li>ссылку на рекомендации (если применимо);</li>
 *   <li>признак «ожидает решения» после исчерпания retry (O-28);</li>
 *   <li>информацию о модели (material/volume/mass/warnings) — для UI;</li>
 *   <li>диагностические поля (сообщение, время обновления).</li>
 * </ul>
 *
 * <p>DTO иммутабельно после сборки через {@link Builder}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "OrchestrationResponse",
        description = "Ответ оркестратора по проекту ТПП")
public class OrchestrationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // Идентификация проекта
    // ------------------------------------------------------------------

    @Schema(description = "Идентификатор проекта", example = "8f3e2c1a-...")
    private String projectId;

    @Schema(description = "Наименование проекта", example = "Корпус редуктора")
    private String projectName;

    @Schema(description = "Обозначение проекта", example = "КР-001")
    private String designation;

    @Schema(description = "Тип объекта изготовления",
            example = "ASSEMBLY",
            allowableValues = {"PART", "ASSEMBLY"})
    private Project.ObjectType objectType;

    // ------------------------------------------------------------------
    // Состояние оркестрации
    // ------------------------------------------------------------------

    @Schema(description = "Текущее состояние конечного автомата",
            example = "PART_PROCESS_PLANNING")
    private String state;

    @Schema(description = "Человекочитаемое имя текущего состояния",
            example = "Проектирование ТП деталей")
    private String stateDisplayName;

    @Schema(description = "Категория состояния: AUTOMATIC / POINT_OF_CONTROL / TERMINAL",
            example = "POINT_OF_CONTROL")
    private String stateCategory;

    @Schema(description = "Признак точки контроля",
            example = "true")
    private Boolean pointOfControl;

    @Schema(description = "Список доступных событий для текущего состояния",
            example = "[\"PART_PROCESS_READY\"]")
    private List<String> availableEvents;

    // ------------------------------------------------------------------
    // Прогресс и текущий этап
    // ------------------------------------------------------------------

    @Schema(description = "Идентификатор текущего этапа (если есть)",
            example = "stage-010")
    private String currentStageId;

    @Schema(description = "Наименование текущего этапа",
            example = "Токарная 010")
    private String currentStageName;

    @Schema(description = "Прогресс выполнения проекта (0–100)", example = "42")
    private Integer progressPercent;

    @Schema(description = "Число пройденных этапов", example = "5")
    private Integer completedStages;

    @Schema(description = "Общее число этапов", example = "12")
    private Integer totalStages;

    // ------------------------------------------------------------------
    // Связанные данные
    // ------------------------------------------------------------------

    @Schema(description = "Идентификатор последнего сформированного ТП",
            example = "tp-001")
    private String technologyProcessId;

    @Schema(description = "Идентификатор активной рекомендации (при дефиците оборудования)",
            example = "rec-123")
    private String recommendationId;

    @Schema(description = "Требуется ли участие пользователя",
            example = "true")
    private Boolean awaitingUserAction;

    @Schema(description = "Сообщение для пользователя (что ожидается или что произошло)",
            example = "Требуется подтвердить ТП деталей")
    private String message;

    // ------------------------------------------------------------------
    // O-28: «Ожидает решения» после исчерпания retry
    // ------------------------------------------------------------------

    @Schema(description = "Проект ожидает решения технолога после исчерпания retry",
            example = "true")
    private Boolean awaitingDecision;

    @Schema(description = "Событие, по которому исчерпаны попытки",
            example = "NORMS_READY")
    private String failedEvent;

    @Schema(description = "Число выполненных попыток", example = "3")
    private Integer attempts;

    // ------------------------------------------------------------------
    // Задача 2: информация о модели (из payload проекта)
    // ------------------------------------------------------------------

    @Schema(description = "Информация о модели: geometryId, material, volume, mass, complexity, warnings, tpType, tpSource, stubCode")
    private Map<String, Object> model;

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    @Schema(description = "Момент последнего обновления состояния проекта")
    private Instant updatedAt;

    @Schema(description = "Дополнительные атрибуты (расширения, диагностика)")
    private Map<String, Object> attributes;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    /** Пустой конструктор для Jackson. */
    public OrchestrationResponse() {
    }

    private OrchestrationResponse(Builder b) {
        this.projectId = b.projectId;
        this.projectName = b.projectName;
        this.designation = b.designation;
        this.objectType = b.objectType;
        this.state = b.state;
        this.stateDisplayName = b.stateDisplayName;
        this.stateCategory = b.stateCategory;
        this.pointOfControl = b.pointOfControl;
        this.availableEvents = b.availableEvents;
        this.currentStageId = b.currentStageId;
        this.currentStageName = b.currentStageName;
        this.progressPercent = b.progressPercent;
        this.completedStages = b.completedStages;
        this.totalStages = b.totalStages;
        this.technologyProcessId = b.technologyProcessId;
        this.recommendationId = b.recommendationId;
        this.awaitingUserAction = b.awaitingUserAction;
        this.message = b.message;
        this.awaitingDecision = b.awaitingDecision;
        this.failedEvent = b.failedEvent;
        this.attempts = b.attempts;
        this.model = b.model;
        this.updatedAt = b.updatedAt;
        this.attributes = b.attributes;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder для удобного создания DTO.
     * Используется в {@code OrchestrationService.buildResponse(...)}.
     */
    public static class Builder {
        private String projectId;
        private String projectName;
        private String designation;
        private Project.ObjectType objectType;
        private String state;
        private String stateDisplayName;
        private String stateCategory;
        private Boolean pointOfControl;
        private List<String> availableEvents;
        private String currentStageId;
        private String currentStageName;
        private Integer progressPercent;
        private Integer completedStages;
        private Integer totalStages;
        private String technologyProcessId;
        private String recommendationId;
        private Boolean awaitingUserAction;
        private String message;
        private Boolean awaitingDecision;
        private String failedEvent;
        private Integer attempts;
        private Map<String, Object> model;
        private Instant updatedAt;
        private Map<String, Object> attributes;

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder designation(String designation) {
            this.designation = designation;
            return this;
        }

        public Builder objectType(Project.ObjectType objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder state(Object state) {
            this.state = state == null ? null : state.toString();
            return this;
        }

        public Builder stateDisplayName(String stateDisplayName) {
            this.stateDisplayName = stateDisplayName;
            return this;
        }

        public Builder stateCategory(String stateCategory) {
            this.stateCategory = stateCategory;
            return this;
        }

        public Builder pointOfControl(Boolean pointOfControl) {
            this.pointOfControl = pointOfControl;
            return this;
        }

        public Builder availableEvents(List<?> availableEvents) {
            this.availableEvents = availableEvents == null
                    ? null
                    : availableEvents.stream().map(Object::toString).toList();
            return this;
        }

        public Builder currentStageId(String currentStageId) {
            this.currentStageId = currentStageId;
            return this;
        }

        public Builder currentStageName(String currentStageName) {
            this.currentStageName = currentStageName;
            return this;
        }

        public Builder progressPercent(Integer progressPercent) {
            this.progressPercent = progressPercent;
            return this;
        }

        public Builder completedStages(Integer completedStages) {
            this.completedStages = completedStages;
            return this;
        }

        public Builder totalStages(Integer totalStages) {
            this.totalStages = totalStages;
            return this;
        }

        public Builder technologyProcessId(String technologyProcessId) {
            this.technologyProcessId = technologyProcessId;
            return this;
        }

        public Builder recommendationId(String recommendationId) {
            this.recommendationId = recommendationId;
            return this;
        }

        public Builder awaitingUserAction(Boolean awaitingUserAction) {
            this.awaitingUserAction = awaitingUserAction;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder awaitingDecision(Boolean awaitingDecision) {
            this.awaitingDecision = awaitingDecision;
            return this;
        }

        public Builder failedEvent(String failedEvent) {
            this.failedEvent = failedEvent;
            return this;
        }

        public Builder attempts(Integer attempts) {
            this.attempts = attempts;
            return this;
        }

        public Builder model(Map<String, Object> model) {
            this.model = model;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public OrchestrationResponse build() {
            return new OrchestrationResponse(this);
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /** Проект завершён. */
    public boolean isCompleted() {
        return "COMPLETED".equals(state);
    }

    /** Проект ожидает решения пользователя. */
    public boolean isAwaitingUser() {
        return Boolean.TRUE.equals(awaitingUserAction)
                || Boolean.TRUE.equals(pointOfControl)
                || Boolean.TRUE.equals(awaitingDecision);
    }

    /** Имеется активная рекомендация. */
    public boolean hasRecommendation() {
        return recommendationId != null && !recommendationId.isBlank();
    }

    // ------------------------------------------------------------------
    // Геттеры и сеттеры
    // ------------------------------------------------------------------

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

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

    public Project.ObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(Project.ObjectType objectType) {
        this.objectType = objectType;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateDisplayName() {
        return stateDisplayName;
    }

    public void setStateDisplayName(String stateDisplayName) {
        this.stateDisplayName = stateDisplayName;
    }

    public String getStateCategory() {
        return stateCategory;
    }

    public void setStateCategory(String stateCategory) {
        this.stateCategory = stateCategory;
    }

    public Boolean getPointOfControl() {
        return pointOfControl;
    }

    public void setPointOfControl(Boolean pointOfControl) {
        this.pointOfControl = pointOfControl;
    }

    public List<String> getAvailableEvents() {
        return availableEvents;
    }

    public void setAvailableEvents(List<String> availableEvents) {
        this.availableEvents = availableEvents;
    }

    public String getCurrentStageId() {
        return currentStageId;
    }

    public void setCurrentStageId(String currentStageId) {
        this.currentStageId = currentStageId;
    }

    public String getCurrentStageName() {
        return currentStageName;
    }

    public void setCurrentStageName(String currentStageName) {
        this.currentStageName = currentStageName;
    }

    public Integer getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(Integer progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getCompletedStages() {
        return completedStages;
    }

    public void setCompletedStages(Integer completedStages) {
        this.completedStages = completedStages;
    }

    public Integer getTotalStages() {
        return totalStages;
    }

    public void setTotalStages(Integer totalStages) {
        this.totalStages = totalStages;
    }

    public String getTechnologyProcessId() {
        return technologyProcessId;
    }

    public void setTechnologyProcessId(String technologyProcessId) {
        this.technologyProcessId = technologyProcessId;
    }

    public String getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(String recommendationId) {
        this.recommendationId = recommendationId;
    }

    public Boolean getAwaitingUserAction() {
        return awaitingUserAction;
    }

    public void setAwaitingUserAction(Boolean awaitingUserAction) {
        this.awaitingUserAction = awaitingUserAction;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getAwaitingDecision() {
        return awaitingDecision;
    }

    public void setAwaitingDecision(Boolean awaitingDecision) {
        this.awaitingDecision = awaitingDecision;
    }

    public String getFailedEvent() {
        return failedEvent;
    }

    public void setFailedEvent(String failedEvent) {
        this.failedEvent = failedEvent;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Map<String, Object> getModel() {
        return model;
    }

    public void setModel(Map<String, Object> model) {
        this.model = model;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    // ------------------------------------------------------------------
    // equals / hashCode / toString
    // ------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrchestrationResponse other)) {
            return false;
        }
        return Objects.equals(projectId, other.projectId)
                && Objects.equals(state, other.state)
                && Objects.equals(updatedAt, other.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, state, updatedAt);
    }

    @Override
    public String toString() {
        return "OrchestrationResponse{" +
                "projectId='" + projectId + '\'' +
                ", projectName='" + projectName + '\'' +
                ", state='" + state + '\'' +
                ", stateCategory='" + stateCategory + '\'' +
                ", availableEvents=" + availableEvents +
                ", awaitingUserAction=" + awaitingUserAction +
                ", awaitingDecision=" + awaitingDecision +
                ", failedEvent='" + failedEvent + '\'' +
                ", attempts=" + attempts +
                ", updatedAt=" + updatedAt +
                '}';
    }
}