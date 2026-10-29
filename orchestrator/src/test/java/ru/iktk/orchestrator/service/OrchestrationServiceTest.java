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
import org.springframework.statemachine.access.StateMachineAccessor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrchestrationService — тесты оркестрации ИКТК")
class OrchestrationServiceTest {

    @Mock
    private StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory;

    @Mock
    private StateMachine<ProjectState, ProjectEvent> stateMachine;

    @Mock
    private StateMachineAccessor<ProjectState, ProjectEvent> stateMachineAccessor;

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

    private static final String PROJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROJECT_NAME = "Корпус редуктора";
    private static final String REQUESTED_BY = "ivanov";

    private ProjectRuntime runtime;
    private final Map<String, ProjectRuntime> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();

        runtime = ProjectRuntime.builder()
                .projectId(PROJECT_ID)
                .projectName(PROJECT_NAME)
                .requestedBy(REQUESTED_BY)
                .state(ProjectState.PROJECT_INIT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(stateMachineFactory.getStateMachine(anyString())).thenReturn(stateMachine);
        when(stateMachine.startReactively()).thenReturn(Mono.empty());
        when(stateMachine.stopReactively()).thenReturn(Mono.empty());
        when(stateMachine.getId()).thenReturn(PROJECT_ID);
        when(stateMachine.getStateMachineAccessor()).thenReturn(stateMachineAccessor);

        State<ProjectState, ProjectEvent> initialState = mock(State.class);
        when(initialState.getId()).thenReturn(ProjectState.PROJECT_INIT);
        when(stateMachine.getState()).thenReturn(initialState);

        when(projectStateRepository.save(any(ProjectRuntime.class)))
                .thenAnswer(inv -> {
                    ProjectRuntime r = inv.getArgument(0);
                    store.put(r.getProjectId(), r);
                    return r;
                });
        when(projectStateRepository.findByProjectId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
    }

    @Nested
    @DisplayName("startProject — создание и запуск проекта")
    class StartProjectTests {

        @Test
        @DisplayName("Успешное создание проекта")
        void startProject_success() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName(PROJECT_NAME)
                    .designation("КР-001")
                    .objectType(Project.ObjectType.ASSEMBLY)
                    .requestedBy(REQUESTED_BY)
                    .plannedProcessTypes(java.util.List.of("CASTING"))
                    .build();

            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            OrchestrationResponse response = orchestrationService.startProject(request);

            assertThat(response).isNotNull();
            assertThat(response.getProjectId()).isNotBlank();
            assertThat(response.getProjectName()).isEqualTo(PROJECT_NAME);

            ArgumentCaptor<ProjectRuntime> runtimeCaptor =
                    ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, times(2)).save(runtimeCaptor.capture());
            ProjectRuntime saved = runtimeCaptor.getValue();
            assertThat(saved.getProjectName()).isEqualTo(PROJECT_NAME);
            assertThat(saved.getRequestedBy()).isEqualTo(REQUESTED_BY);
            assertThat(saved.getState()).isEqualTo(ProjectState.PROJECT_INIT);

            verify(decisionLogService).log(
                    anyString(),
                    eq("PROJECT_STARTED"),
                    any(Map.class),
                    anyString());
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

    @Nested
    @DisplayName("run — продолжение оркестрации")
    class RunTests {

        @Test
        @DisplayName("Успешное продолжение: маршрутизация по текущему состоянию")
        void run_success() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            doAnswer(inv -> null).when(transitionHandler)
                    .route(anyString(), any(), any(), any());

            OrchestrationResponse response = orchestrationService.run(PROJECT_ID);

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
                    any(Map.class),
                    anyString());
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

    @Nested
    @DisplayName("approve — подтверждение точки контроля")
    class ApproveTests {

        @Test
        @DisplayName("Подтверждение на точке контроля: переход по событию")
        void approve_success() {
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));

            State<ProjectState, ProjectEvent> cpState = mock(State.class);
            when(cpState.getId()).thenReturn(ProjectState.PART_PROCESS_PLANNING);
            when(stateMachine.getState()).thenReturn(cpState);

            when(transitionHandler.nextOnApprove(ProjectState.PART_PROCESS_PLANNING))
                    .thenReturn(ProjectEvent.PART_PROCESS_READY);
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            OrchestrationResponse response = orchestrationService.approve(PROJECT_ID, "ОК");

            assertThat(response).isNotNull();
            verify(decisionLogService).log(
                    eq(PROJECT_ID),
                    eq("APPROVED"),
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
        }
    }

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
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
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
                    any(Map.class),
                    anyString());
        }
    }

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

    // =========================================================================
    // 10. Дополнительные ветки (PATCHED_EXTRA_TESTS)
    // =========================================================================

    @Nested
    @DisplayName("Дополнительные ветки OrchestrationService")
    class ExtraBranchTests {

        @Test
        @DisplayName("startProject с geometryId: payload содержит objectRef")
        void startProject_withGeometryId_payloadContainsObjectRef() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName(PROJECT_NAME)
                    .geometryId("geom-42")
                    .requestedBy(REQUESTED_BY)
                    .plannedProcessTypes(java.util.List.of("CASTING"))
                    .build();

            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            orchestrationService.startProject(request);

            ArgumentCaptor<ProjectRuntime> captor = ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, atLeastOnce()).save(captor.capture());
            ProjectRuntime saved = captor.getAllValues().get(0);
            assertThat(saved.getPayload()).containsEntry("objectRef", "geom-42");
        }

        @Test
        @DisplayName("onModuleResult: attempt == MAX_RETRIES (граница 3) -> moveToAwaitingDecision")
        void onModuleResult_attemptEqualsMax_goesToAwaitingDecision() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(3);

            orchestrationService.onModuleResult(
                    PROJECT_ID, ProjectEvent.NORMS_READY, Map.of("err", "x"), false);

            verify(moduleCommandProducer, never()).retry(anyString(), any(), any());
            verify(transitionHandler).moveToAwaitingDecision(any(), eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY));
        }

        @Test
        @DisplayName("dispatchEvent: auto-state с nextAuto != null -> рекурсивный переход")
        void dispatchEvent_autoState_recurses() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class)))
                    .thenReturn(true);

            State<ProjectState, ProjectEvent> s1 = mock(State.class);
            when(s1.getId()).thenReturn(ProjectState.GEOMETRY_ANALYSIS);
            State<ProjectState, ProjectEvent> s2 = mock(State.class);
            when(s2.getId()).thenReturn(ProjectState.NORMING);
            when(stateMachine.getState()).thenReturn(s1, s2, s2);

            when(transitionHandler.isAutoState(ProjectState.GEOMETRY_ANALYSIS)).thenReturn(true);
            when(transitionHandler.nextAuto(ProjectState.GEOMETRY_ANALYSIS)).thenReturn(ProjectEvent.IS_PART);

            orchestrationService.onModuleResult(
                    PROJECT_ID, ProjectEvent.IS_PART, Map.of(), true);

            verify(stateMachine, atLeast(2)).sendEvent(any(org.springframework.messaging.Message.class));
        }

        @Test
        @DisplayName("obtainMachine: повторный вызов возвращает тот же StateMachine (кэш)")
        void obtainMachine_cachesPerProject() {
            runtime.setState(ProjectState.PROJECT_INIT);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));

            orchestrationService.getSummary(PROJECT_ID);
            orchestrationService.getSummary(PROJECT_ID);

            verify(stateMachineFactory, times(1)).getStateMachine(anyString());
        }

        @Test
        @DisplayName("currentActor: читает X-User-Id из HTTP-запроса")
        void currentActor_readsHeader() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID))
                    .thenReturn(Optional.of(runtime));
            doAnswer(inv -> null).when(transitionHandler).route(anyString(), any(), any(), any());

            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.addHeader("X-User-Id", "user-777");
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                    new org.springframework.web.context.request.ServletRequestAttributes(request));
            try {
                orchestrationService.run(PROJECT_ID);
            } finally {
                org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            }

            verify(decisionLogService).log(
                    eq(PROJECT_ID), eq("ORCHESTRATION_RUN"), any(Map.class), eq("user-777"));
        }
    }    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("startProject: PART without plannedProcessTypes throws")
    void startProject_partWithoutTp_throws() {
        OrchestrationRequest request = OrchestrationRequest.builder()
                .projectName("Part")
                .objectType(Project.ObjectType.PART)
                .build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrationService.startProject(request))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("plannedProcessTypes");
    }

    @Nested
    @DisplayName("Sequential forging -> machining (сессия 32)")
    class SequentialTests {

        @Test
        @DisplayName("startProject 2 ТП -> tpType=FORGING, pendingProcessTypes=[MACHINING]")
        void startProject_multiTp_pendingPopulated() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName("Seq")
                    .requestedBy("ivanov")
                    .plannedProcessTypes(List.of("FORGING", "MACHINING"))
                    .build();

            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);
            orchestrationService.startProject(request);

            ArgumentCaptor<ProjectRuntime> captor = ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, atLeast(1)).save(captor.capture());
            ProjectRuntime saved = captor.getAllValues().stream()
                    .filter(r -> r.getPayload() != null && r.getPayload().containsKey("pendingProcessTypes"))
                    .findFirst().orElseThrow();
            assertThat(saved.getPayload().get("tpType")).isEqualTo("FORGING");
            List<?> pendingSaved = (List<?>) saved.getPayload().get("pendingProcessTypes");
            assertThat(pendingSaved).hasSize(1);
            assertThat(pendingSaved.get(0)).isEqualTo("MACHINING");
        }

        @Test
        @DisplayName("onModuleResult PART_PROCESS_READY + pending -> route next, БЕЗ sendEvent")
        void onModuleResult_sequential_routeToNext() {
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            Map<String, Object> p = new HashMap<>();
            p.put("tpType", "FORGING");
            p.put("pendingProcessTypes", new java.util.ArrayList<>(List.of("MACHINING")));
            runtime.setPayload(p);
            store.put(PROJECT_ID, runtime);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY,
                    new HashMap<>(), true);

            verify(transitionHandler).route(eq(PROJECT_ID), eq(ProjectState.PART_PROCESS_PLANNING),
                    any(StateMachine.class), any(Map.class));
            verify(stateMachine, never()).sendEvent(any(org.springframework.messaging.Message.class));
            verify(decisionLogService).log(eq(PROJECT_ID), eq("SEQUENTIAL_NEXT"), any(Map.class), any());
            ArgumentCaptor<ProjectRuntime> captor = ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, atLeast(1)).save(captor.capture());
            ProjectRuntime saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(saved.getPayload().get("tpType")).isEqualTo("MACHINING");
            assertThat(saved.getPayload().containsKey("pendingProcessTypes")).isFalse();
        }

        @Test
        @DisplayName("onModuleResult sequential: MODULE_RESULT_OK для промежуточного ТП пишется в decision_log")
        void onModuleResult_sequential_logsModuleResultOk() {
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            Map<String, Object> p = new HashMap<>();
            p.put("tpType", "FORGING");
            p.put("pendingProcessTypes", new java.util.ArrayList<>(List.of("MACHINING")));
            runtime.setPayload(p);
            store.put(PROJECT_ID, runtime);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY,
                    new HashMap<>(), true);

            verify(decisionLogService).log(eq(PROJECT_ID), eq("MODULE_RESULT_OK"), any(Map.class), any());
            verify(decisionLogService).log(eq(PROJECT_ID), eq("SEQUENTIAL_NEXT"), any(Map.class), any());
        }

        @Test
        @DisplayName("onModuleResult PART_PROCESS_READY без pending -> legacy dispatchEvent")
        void onModuleResult_lastSequential_sendEvent() {
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            Map<String, Object> p = new HashMap<>();
            p.put("tpType", "MACHINING");
            runtime.setPayload(p);
            store.put(PROJECT_ID, runtime);

            State<ProjectState, ProjectEvent> tooling = mock(State.class);
            when(tooling.getId()).thenReturn(ProjectState.TOOLING_DESIGN);
            when(stateMachine.getState()).thenReturn(tooling);
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY,
                    new HashMap<>(), true);

            verify(stateMachine).sendEvent(any(org.springframework.messaging.Message.class));
            verify(decisionLogService, never()).log(eq(PROJECT_ID), eq("SEQUENTIAL_NEXT"), any(Map.class), any());
        }

        @Test
        @DisplayName("startProject 4 ТП -> OrchestrationException (максимум 3)")
        void startProject_4Tp_throws() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName("Too many")
                    .requestedBy("ivanov")
                    .plannedProcessTypes(List.of("CASTING", "FORGING", "MACHINING", "CASTING"))
                    .build();

            assertThatThrownBy(() -> orchestrationService.startProject(request))
                    .isInstanceOf(OrchestrationException.class)
                    .hasMessageContaining("не более 3");
        }
    }
}
