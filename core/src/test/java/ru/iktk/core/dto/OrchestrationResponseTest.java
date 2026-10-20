package ru.iktk.core.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Project;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrchestrationResponse — DTO ответа")
class OrchestrationResponseTest {

    @Test
    @DisplayName("Пустой конструктор — все поля null")
    void defaultConstructor_allNull() {
        OrchestrationResponse r = new OrchestrationResponse();
        assertThat(r.getProjectId()).isNull();
        assertThat(r.getState()).isNull();
        assertThat(r.getAvailableEvents()).isNull();
        assertThat(r.getAwaitingUserAction()).isNull();
    }

    @Test
    @DisplayName("Builder заполняет все поля")
    void builder_setsAllFields() {
        Instant now = Instant.now();
        Map<String, Object> attrs = Map.of("k", "v");

        OrchestrationResponse r = OrchestrationResponse.builder()
                .projectId("p1")
                .projectName("Корпус")
                .designation("КР-001")
                .objectType(Project.ObjectType.ASSEMBLY)
                .state("PART_PROCESS_PLANNING")
                .stateDisplayName("Проектирование ТП деталей")
                .stateCategory("POINT_OF_CONTROL")
                .pointOfControl(true)
                .availableEvents(List.of("PART_PROCESS_READY"))
                .currentStageId("stage-010")
                .currentStageName("Токарная 010")
                .progressPercent(42)
                .completedStages(5)
                .totalStages(12)
                .technologyProcessId("tp-1")
                .recommendationId("rec-1")
                .awaitingUserAction(true)
                .message("Требуется подтверждение")
                .updatedAt(now)
                .attributes(attrs)
                .build();

        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getState()).isEqualTo("PART_PROCESS_PLANNING");
        assertThat(r.getStateDisplayName()).isEqualTo("Проектирование ТП деталей");
        assertThat(r.getStateCategory()).isEqualTo("POINT_OF_CONTROL");
        assertThat(r.getPointOfControl()).isTrue();
        assertThat(r.getAvailableEvents()).containsExactly("PART_PROCESS_READY");
        assertThat(r.getCurrentStageId()).isEqualTo("stage-010");
        assertThat(r.getCurrentStageName()).isEqualTo("Токарная 010");
        assertThat(r.getProgressPercent()).isEqualTo(42);
        assertThat(r.getCompletedStages()).isEqualTo(5);
        assertThat(r.getTotalStages()).isEqualTo(12);
        assertThat(r.getTechnologyProcessId()).isEqualTo("tp-1");
        assertThat(r.getRecommendationId()).isEqualTo("rec-1");
        assertThat(r.getAwaitingUserAction()).isTrue();
        assertThat(r.getMessage()).isEqualTo("Требуется подтверждение");
        assertThat(r.getUpdatedAt()).isEqualTo(now);
        assertThat(r.getAttributes()).isEqualTo(attrs);
    }

    @Test
    @DisplayName("state(Object): enum -> toString, null -> null")
    void stateObject_conversion() {
        OrchestrationResponse r = OrchestrationResponse.builder()
                .state(Project.ObjectType.ASSEMBLY).build();
        assertThat(r.getState()).isEqualTo("ASSEMBLY");

        OrchestrationResponse nullState = OrchestrationResponse.builder().state((Object) null).build();
        assertThat(nullState.getState()).isNull();
    }

    @Test
    @DisplayName("availableEvents(List<?>): любой список -> List<String>")
    void availableEvents_conversion() {
        OrchestrationResponse r = OrchestrationResponse.builder()
                .availableEvents(List.of(Project.ObjectType.PART, Project.ObjectType.ASSEMBLY))
                .build();
        assertThat(r.getAvailableEvents()).containsExactly("PART", "ASSEMBLY");

        OrchestrationResponse nullEvents = OrchestrationResponse.builder()
                .availableEvents(null).build();
        assertThat(nullEvents.getAvailableEvents()).isNull();
    }

    @Test
    @DisplayName("isCompleted: только для state=COMPLETED")
    void isCompleted() {
        assertThat(OrchestrationResponse.builder().state("COMPLETED").build().isCompleted()).isTrue();
        assertThat(OrchestrationResponse.builder().state("NORMING").build().isCompleted()).isFalse();
        assertThat(new OrchestrationResponse().isCompleted()).isFalse();
    }

    @Test
    @DisplayName("isAwaitingUser: true при awaitingUserAction или pointOfControl")
    void isAwaitingUser() {
        assertThat(OrchestrationResponse.builder().awaitingUserAction(true).build().isAwaitingUser()).isTrue();
        assertThat(OrchestrationResponse.builder().pointOfControl(true).build().isAwaitingUser()).isTrue();
        assertThat(OrchestrationResponse.builder()
                .awaitingUserAction(false).pointOfControl(false).build().isAwaitingUser()).isFalse();
        assertThat(new OrchestrationResponse().isAwaitingUser()).isFalse();
    }

    @Test
    @DisplayName("hasRecommendation: null/blank -> false, значение -> true")
    void hasRecommendation() {
        assertThat(new OrchestrationResponse().hasRecommendation()).isFalse();
        assertThat(OrchestrationResponse.builder().recommendationId("   ").build().hasRecommendation()).isFalse();
        assertThat(OrchestrationResponse.builder().recommendationId("rec-1").build().hasRecommendation()).isTrue();
    }

    @Test
    @DisplayName("equals/hashCode по projectId, state, updatedAt")
    void equals_hashCode_byKeyFields() {
        Instant now = Instant.now();
        OrchestrationResponse a = OrchestrationResponse.builder()
                .projectId("p1").state("NORMING").updatedAt(now).projectName("A").build();
        OrchestrationResponse b = OrchestrationResponse.builder()
                .projectId("p1").state("NORMING").updatedAt(now).projectName("B").build();

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    @DisplayName("equals: разница в state -> не равны")
    void equals_diffState() {
        Instant now = Instant.now();
        OrchestrationResponse a = OrchestrationResponse.builder()
                .projectId("p1").state("NORMING").updatedAt(now).build();
        OrchestrationResponse b = OrchestrationResponse.builder()
                .projectId("p1").state("DOCUMENTATION").updatedAt(now).build();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals: сравнение с null и с самим собой")
    void equals_nullAndSelf() {
        OrchestrationResponse a = OrchestrationResponse.builder().projectId("p1").build();
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    @DisplayName("toString содержит ключевые поля")
    void toString_containsFields() {
        OrchestrationResponse r = OrchestrationResponse.builder()
                .projectId("p1").projectName("Корпус")
                .state("NORMING").stateCategory("AUTOMATIC")
                .awaitingUserAction(true).build();
        assertThat(r.toString()).contains("p1", "Корпус", "NORMING", "AUTOMATIC", "true");
    }

    @Test
    @DisplayName("Сеттеры меняют значения")
    void setters_work() {
        OrchestrationResponse r = new OrchestrationResponse();
        r.setProjectId("p1");
        r.setState("NORMING");
        r.setStateCategory("AUTOMATIC");
        r.setAwaitingUserAction(true);
        r.setAvailableEvents(List.of("NORMS_READY"));

        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getState()).isEqualTo("NORMING");
        assertThat(r.getStateCategory()).isEqualTo("AUTOMATIC");
        assertThat(r.getAwaitingUserAction()).isTrue();
        assertThat(r.getAvailableEvents()).containsExactly("NORMS_READY");
    }
}