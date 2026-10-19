package ru.iktk.orchestrator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import reactor.core.publisher.Mono;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;
import ru.iktk.core.domain.Project;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.messaging.ModuleCommandProducer;
import ru.iktk.orchestrator.model.ProjectRuntime;
import ru.iktk.orchestrator.repository.ProjectStateRepository;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;
import ru.iktk.orchestrator.statemachine.TransitionHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты для {@link OrchestrationService}.
 *
 * <p>Покрывают:
 * <ul>
 *   <li>создание и запуск проекта ({@code startProject});</li>
 *   <li>продолжение оркестрации ({@code run});</li>
 *   <li>чтение состояния и сводки ({@code getState}, {@code getSummary});</li>
 *   <li>точки контроля: {@code approve}, {@code sendEvent},
 *       {@code acceptRecommendation}, {@code adjustProcess},
 *       {@code selectOutsourcing};</li>
 *   <li>откат версии ({@code rollback});</li>
 *   <li>обработку результата модуля ({@code onModuleResult});</li>
 *   <li>обработку ошибок и повторных попыток модулей;</li>
 *   <li>валидацию входных данных.</li>
 * </ul>
 *
 * <p>Тесты используют Mockito для изоляции от реального конечного автомата,
 * очередей и БД. State machine подменяется mock-объектом.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrchestrationService — тесты оркестрации ИКТК")
class OrchestrationServiceTest {

    // -------------------------------------------------------------------------
    // Моки зависимостей
    // -------------------------------------------------------------------------

    @Mock
    private StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory;

    @Mock
    private StateMachine<ProjectState, ProjectEvent> stateMachine;

    @Mock
    private ProjectStateRepository projectStateRepository;

    @Mock
    private ModuleCommandProducer moduleCommandProducer;

    @Mock
    private TransitionHandler transitionHandler;

    @Mock
    private DecisionLogService decisionLogService;

    @InjectMocks
    private OrchestrationService orchestrationService;

    // -------------------------------------------------------------------------
    // Тестовые данные
    // -------------------------------------------------------------------------

    private static final String PROJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROJECT_NAME = "Корпус редуктора";
    private static final String REQUESTED_BY = "ivanov";

    private ProjectRuntime runtime;

    @BeforeEach
    void setUp() {
    runtime = ProjectRuntime.builder()
            .projectId(PROJECT_ID)
            .projectName(PROJECT_NAME)
            .requestedBy(REQUESTED_BY)
            .state(ProjectState.PROJECT_INIT)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    // Базовая настройка state machine
    when(stateMachineFactory.getStateMachine(anyString())).thenReturn(stateMachine);
    when(stateMachine.startReactively()).thenReturn(Mono.empty());
    when(stateMachine.stopReactively()).thenReturn(Mono.empty());
    when(stateMachine.getId()).thenReturn(PROJECT_ID);

    State<ProjectState, ProjectEvent> initialState = mock(State.class);
    when(initialState.getId()).thenReturn(ProjectState.PROJECT_INIT);
    when(stateMachine.getState()).thenReturn(initialState);
    }

    // =========================================================================
    // 1. startProject
    // =========================================================================

    @Nested
    @DisplayName("startProject — создание и запуск проекта")
    class StartProjectTests {

        @Test
        @DisplayName("Успешное создание проекта: сохраняет runtime, запускает автомат, "
                + "публикует событие START_GEOMETRY_INPUT")
        void startProject_success() {
            // given
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName(PROJECT_NAME)
                    .designation("КР-001")
                    .objectType(Project.ObjectType.ASSEMBLY)
                    .requestedBy(REQUESTED_BY)
                    .build();

            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            // when
            OrchestrationResponse response = orchestrationService.startProject(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getProjectId()).isNotBlank();
            assertThat(response.getProjectName()).isEqualTo(PROJECT_NAME);

            ArgumentCaptor<ProjectRuntime> runtimeCaptor =
                    ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, times(1)).save(runtimeCaptor.capture());
            ProjectRuntime saved = runtimeCaptor.getValue();
            assertThat(saved.getProjectName()).isEqualTo(PROJECT_NAME);
            assertThat(saved.getRequestedBy()).isEqualTo(REQUESTED_BY);
            assertThat(saved.getState()).isEqualTo(ProjectState.PROJECT_INIT);

            verify(decisionLogService).log(
                    anyString(),
                    eq("PROJECT_STARTED"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Ошибка валидации: пустое имя проекта")
        void startProject_emptyName_throws() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName("")
                    .build();

            assertThatThrownBy(() -> orchestrationService.startProject(request))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("имя проекта");
        }

        @Test
        @DisplayName("Ошибка валидации: null-запрос")
        void startProject_nullRequest_throws() {
            assertThatThrownBy(() -> orchestrationService.startProject(null))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("пуст");
        }
    }

    // =========================================================================
    // 2. run
    // =========================================================================

    @Nested
    @DisplayName("run — продолжение оркестрации")
    class RunTests {

        @Test
        @DisplayName("Успешное продолжение: маршрутизация по текущему состоянию")
        void run_success() {
            // given
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            doAnswer(inv -> null).when(transitionHandler)
                    .route(anyString(), any(), any(), any());

            // when
            OrchestrationResponse response = orchestrationService.run(PROJECT_ID);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getProjectId()).isEqualTo(PROJECT_ID);

            verify(transitionHandler).route(
                    eq(PROJECT_ID),
                    eq(ProjectState.GEOMETRY_ANALYSIS),
                    any(StateMachine.class),
                    any());
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("ORCHESTRATION_RUN"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Ошибка: проект не найден")
        void run_projectNotFound_throws() {
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrationService.run(PROJECT_ID))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("не найден");
        }
    }

    // =========================================================================
    // 3. getState / getSummary
    // =========================================================================

    @Nested
    @DisplayName("Чтение состояния и сводки")
    class ReadStateTests {

        @Test
        @DisplayName("getState возвращает текущее состояние проекта")
        void getState_success() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));

            ProjectState state = orchestrationService.getState(PROJECT_ID);

            assertThat(state).isEqualTo(ProjectState.NORMING);
        }

        @Test
        @DisplayName("getState: проект не найден")
        void getState_projectNotFound() {
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrationService.getState(PROJECT_ID))
                    .isInstanceOf(OrchestrationException.class);
        }

        @Test
        @DisplayName("getSummary возвращает корректный ответ")
        void getSummary_success() {
            runtime.setState(ProjectState.TOOLING_DESIGN);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.availableEvents(ProjectState.TOOLING_DESIGN))
                    .thenReturn(List.of(ProjectEvent.TOOLING_READY));

            OrchestrationResponse response = orchestrationService.getSummary(PROJECT_ID);

            assertThat(response.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(response.getProjectName()).isEqualTo(PROJECT_NAME);
        }
    }

    // =========================================================================
    // 4. approve (точка контроля)
    // =========================================================================

    @Nested
    @DisplayName("approve — подтверждение точки контроля")
    class ApproveTests {

        @Test
        @DisplayName("Подтверждение на точке контроля: переход по событию")
        void approve_success() {
            // given
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.nextOnApprove(ProjectState.PART_PROCESS_PLANNING))
                    .thenReturn(ProjectEvent.PART_PROCESS_READY);
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            // when
            OrchestrationResponse response = orchestrationService.approve(PROJECT_ID, "ОК");

            // then
            assertThat(response).isNotNull();
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("APPROVED"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Ошибка: подтверждение не предусмотрено для состояния")
        void approve_notAllowed_throws() {
            runtime.setState(ProjectState.COMPLETED);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.nextOnApprove(ProjectState.COMPLETED))
                    .thenReturn(null);

            assertThatThrownBy(() -> orchestrationService.approve(PROJECT_ID, null))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("подтверждение");
        }
    }

    // =========================================================================
    // 5. sendEvent
    // =========================================================================

    @Nested
    @DisplayName("sendEvent — внешнее событие")
    class SendEventTests {

        @Test
        @DisplayName("Успешная отправка события")
        void sendEvent_success() {
            runtime.setState(ProjectState.RECOMMENDATION_REVIEW);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            OrchestrationResponse response = orchestrationService.sendEvent(
                    PROJECT_ID,
                    ProjectEvent.RECOMMENDATION_ACCEPTED,
                    Map.of("recommendationId", "rec-1"));

            assertThat(response).isNotNull();
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("EVENT_SENT"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Событие не принято автоматом")
        void sendEvent_notAccepted_throws() {
            runtime.setState(ProjectState.PROJECT_INIT);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(false);

            assertThatThrownBy(() -> orchestrationService.sendEvent(
                    PROJECT_ID,
                    ProjectEvent.CONTROL_PASSED,
                    null))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("не принято");
        }
    }

    // =========================================================================
    // 6. Специализированные точки контроля
    // =========================================================================

    @Nested
    @DisplayName("Точки контроля рекомендаций")
    class RecommendationTests {

        @Test
        @DisplayName("acceptRecommendation: событие RECOMMENDATION_ACCEPTED")
        void acceptRecommendation_success() {
            runtime.setState(ProjectState.RECOMMENDATION_REVIEW);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            orchestrationService.acceptRecommendation(
                    PROJECT_ID, "rec-1", Map.of("comment", "ОК"));

            verify(stateMachine).sendEvent(any(org.springframework.messaging.Message.class));
        }

        @Test
        @DisplayName("adjustProcess: событие PROCESS_ADJUSTED")
        void adjustProcess_success() {
            runtime.setState(ProjectState.RECOMMENDATION_REVIEW);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            orchestrationService.adjustProcess(
                    PROJECT_ID, "rec-1", Map.of("newEquipmentId", "eq-5"));

            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("EVENT_SENT"),
                    any(Map.class));
        }

        @Test
        @DisplayName("selectOutsourcing: событие OUTSOURCING_SELECTED")
        void selectOutsourcing_success() {
            runtime.setState(ProjectState.RECOMMENDATION_REVIEW);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            orchestrationService.selectOutsourcing(
                    PROJECT_ID, "rec-1", Map.of("contractor", "ООО Подряд"));

            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("EVENT_SENT"),
                    any(Map.class));
        }
    }

    // =========================================================================
    // 7. rollback
    // =========================================================================

    @Nested
    @DisplayName("rollback — откат к версии")
    class RollbackTests {

        @Test
        @DisplayName("Успешный откат: сброс автомата на целевую версию")
        void rollback_success() {
            ProjectRuntime target = ProjectRuntime.builder()
                    .projectId(PROJECT_ID)
                    .projectName(PROJECT_NAME)
                    .state(ProjectState.TOOLING_DESIGN)
                    .updatedAt(Instant.now())
                    .build();

            when(projectStateRepository.findByProjectIdAndVersion(PROJECT_ID, "v3"))
                    .thenReturn(Optional.of(target));
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(target));

            OrchestrationResponse response = orchestrationService.rollback(PROJECT_ID, "v3");

            assertThat(response).isNotNull();
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("ROLLBACK"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Ошибка: версия не найдена")
        void rollback_versionNotFound() {
            when(projectStateRepository.findByProjectIdAndVersion(PROJECT_ID, "v99"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orchestrationService.rollback(PROJECT_ID, "v99"))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("Версия не найдена");
        }
    }

    // =========================================================================
    // 8. onModuleResult
    // =========================================================================

    @Nested
    @DisplayName("onModuleResult — результат от модуля")
    class ModuleResultTests {

        @Test
        @DisplayName("Успешный результат: переход по событию")
        void onModuleResult_success() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            orchestrationService.onModuleResult(
                    PROJECT_ID,
                    ProjectEvent.IS_PART,
                    Map.of("result", "ok"),
                    true);

            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("MODULE_RESULT_OK"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Неуспешный результат: запуск retry")
        void onModuleResult_failure_retries() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(1);

            orchestrationService.onModuleResult(
                    PROJECT_ID,
                    ProjectEvent.NORMS_READY,
                    Map.of("error", "timeout"),
                    false);

            verify(moduleCommandProducer).retry(eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY), any());
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("MODULE_RESULT_FAIL"),
                    any(Map.class));
        }

        @Test
        @DisplayName("Исчерпание попыток: перевод в 'Ожидает решения'")
        void onModuleResult_failure_attemptsExhausted() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(3);

            orchestrationService.onModuleResult(
                    PROJECT_ID,
                    ProjectEvent.NORMS_READY,
                    Map.of("error", "timeout"),
                    false);

            verify(transitionHandler).moveToAwaitingDecision(any(), eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY));
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("MODULE_FAILED"),
                    any(Map.class));
        }
    }

    // =========================================================================
    // 9. Диагностика
    // =========================================================================

    @Nested
    @DisplayName("Диагностика")
    class DiagnosticsTests {

        @Test
        @DisplayName("activeProjects: возвращает список активных проектов")
        void activeProjects_returnsList() {
            List<String> projects = orchestrationService.activeProjects();

            assertThat(projects).isNotNull();
        }
    }
}
