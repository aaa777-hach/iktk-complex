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
    @DisplayName("builder: все поля заданы -> геттеры возвращают значения")
    void builder_allFields_set() {
        Instant now = Instant.now();
        OrchestrationResponse r = OrchestrationResponse.builder()
                .projectId("p1")
                .projectName("Корпус")
                .designation("КР-001")
                .objectType(Project.ObjectType.PART)
                .state("TOOLING_DESIGN")
                .stateDisplayName("Проектирование оснастки")
                .stateCategory("POINT_OF_CONTROL")
                .pointOfControl(true)
                .availableEvents(List.of("TOOLING_READY"))
                .currentStageId("st-1")
                .currentStageName("Токарная 010")
                .progressPercent(42)
                .completedStages(5)
                .totalStages(12)
                .technologyProcessId("tp-1")
                .recommendationId("rec-1")
                .awaitingUserAction(true)
                .message("msg")
                .awaitingDecision(true)
                .failedEvent("NORMS_READY")
                .attempts(3)
                .model(Map.of("material", "Steel 45"))
                .updatedAt(now)
                .attributes(Map.of("k", "v"))
                .build();

        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getProjectName()).isEqualTo("Корпус");
        assertThat(r.getDesignation()).isEqualTo("КР-001");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.PART);
        assertThat(r.getState()).isEqualTo("TOOLING_DESIGN");
        assertThat(r.getStateDisplayName()).isEqualTo("Проектирование оснастки");
        assertThat(r.getStateCategory()).isEqualTo("POINT_OF_CONTROL");
        assertThat(r.getPointOfControl()).isTrue();
        assertThat(r.getAvailableEvents()).containsExactly("TOOLING_READY");
        assertThat(r.getCurrentStageId()).isEqualTo("st-1");
        assertThat(r.getCurrentStageName()).isEqualTo("Токарная 010");
        assertThat(r.getProgressPercent()).isEqualTo(42);
        assertThat(r.getCompletedStages()).isEqualTo(5);
        assertThat(r.getTotalStages()).isEqualTo(12);
        assertThat(r.getTechnologyProcessId()).isEqualTo("tp-1");
        assertThat(r.getRecommendationId()).isEqualTo("rec-1");
        assertThat(r.getAwaitingUserAction()).isTrue();
        assertThat(r.getMessage()).isEqualTo("msg");
        assertThat(r.getAwaitingDecision()).isTrue();
        assertThat(r.getFailedEvent()).isEqualTo("NORMS_READY");
        assertThat(r.getAttempts()).isEqualTo(3);
        assertThat(r.getModel()).containsEntry("material", "Steel 45");
        assertThat(r.getUpdatedAt()).isEqualTo(now);
        assertThat(r.getAttributes()).containsEntry("k", "v");
    }

    @Test
    @DisplayName("builder: пустой -> все null/false")
    void builder_empty_defaults() {
        OrchestrationResponse r = OrchestrationResponse.builder().build();
        assertThat(r.getProjectId()).isNull();
        assertThat(r.getState()).isNull();
        assertThat(r.getAvailableEvents()).isNull();
        assertThat(r.getPointOfControl()).isNull();
        assertThat(r.getAwaitingUserAction()).isNull();
        assertThat(r.getAwaitingDecision()).isNull();
        assertThat(r.getFailedEvent()).isNull();
        assertThat(r.getAttempts()).isNull();
        assertThat(r.getModel()).isNull();

        assertThat(r.isCompleted()).isFalse();
        assertThat(r.isAwaitingUser()).isFalse();
        assertThat(r.hasRecommendation()).isFalse();
    }

    @Test
    @DisplayName("builder: state(Object) -> toString; availableEvents(List<?>) -> List<String>")
    void builder_converters() {
        OrchestrationResponse r = OrchestrationResponse.builder()
                .state(Project.ObjectType.PART)
                .availableEvents(List.of(1, "two"))
                .build();
        assertThat(r.getState()).isEqualTo("PART");
        assertThat(r.getAvailableEvents()).containsExactly("1", "two");
    }

    @Test
    @DisplayName("builder: null в state/availableEvents")
    void builder_nullConversions() {
        OrchestrationResponse r = OrchestrationResponse.builder()
                .state((Object) null)
                .availableEvents(null)
                .build();
        assertThat(r.getState()).isNull();
        assertThat(r.getAvailableEvents()).isNull();
    }

    @Test
    @DisplayName("isCompleted: true только для COMPLETED")
    void isCompleted_cases() {
        OrchestrationResponse r = new OrchestrationResponse();
        assertThat(r.isCompleted()).isFalse();
        r.setState("TOOLING_DESIGN");
        assertThat(r.isCompleted()).isFalse();
        r.setState("COMPLETED");
        assertThat(r.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("isAwaitingUser: awaitingUserAction/pointOfControl/awaitingDecision")
    void isAwaitingUser_cases() {
        OrchestrationResponse r = new OrchestrationResponse();
        assertThat(r.isAwaitingUser()).isFalse();

        r.setAwaitingUserAction(true);
        assertThat(r.isAwaitingUser()).isTrue();

        r = new OrchestrationResponse();
        r.setPointOfControl(true);
        assertThat(r.isAwaitingUser()).isTrue();

        r = new OrchestrationResponse();
        r.setAwaitingDecision(true);
        assertThat(r.isAwaitingUser()).isTrue();
    }

    @Test
    @DisplayName("hasRecommendation: null/blank/ok")
    void hasRecommendation_cases() {
        OrchestrationResponse r = new OrchestrationResponse();
        assertThat(r.hasRecommendation()).isFalse();
        r.setRecommendationId("");
        assertThat(r.hasRecommendation()).isFalse();
        r.setRecommendationId("  ");
        assertThat(r.hasRecommendation()).isFalse();
        r.setRecommendationId("rec-1");
        assertThat(r.hasRecommendation()).isTrue();
    }

    @Test
    @DisplayName("equals/hashCode/toString")
    void equals_hashCode_toString() {
        Instant now = Instant.now();
        OrchestrationResponse a = OrchestrationResponse.builder()
                .projectId("p1").state("NORMING").updatedAt(now).build();
        OrchestrationResponse b = OrchestrationResponse.builder()
                .projectId("p1").state("NORMING").updatedAt(now).build();
        OrchestrationResponse c = OrchestrationResponse.builder()
                .projectId("p2").state("COMPLETED").updatedAt(now).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null).isNotEqualTo("other");
        assertThat(a).isEqualTo(a);

        assertThat(a.toString())
                .contains("p1").contains("NORMING");
    }
}