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
import ru.iktk.core.domain.Project;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.config.OrchestratorProperties;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrchestrationService — потоковые сценарии (module result, sequential, getModel)")
class OrchestrationServiceFlowTest {

    @Mock private StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory;
    @Mock private StateMachine<ProjectState, ProjectEvent> stateMachine;
    @Mock private StateMachineAccessor<ProjectState, ProjectEvent> stateMachineAccessor;
    @Mock private ProjectStateRepository projectStateRepository;
    @Mock private ModuleCommandProducer moduleCommandProducer;
    @Mock private TransitionHandler transitionHandler;
    @Mock private DecisionLogService decisionLogService;
    @Mock private OrchestratorProperties orchestratorProperties;

    @InjectMocks private OrchestrationService orchestrationService;

    private static final String PROJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROJECT_NAME = "Корпус редуктора";
    private static final String REQUESTED_BY = "ivanov";

    private ProjectRuntime runtime;
    private final Map<String, ProjectRuntime> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        store.clear();
        runtime = ProjectRuntime.builder()
                .projectId(PROJECT_ID).projectName(PROJECT_NAME).requestedBy(REQUESTED_BY)
                .state(ProjectState.PROJECT_INIT).createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(stateMachineFactory.getStateMachine(anyString())).thenReturn(stateMachine);
        when(stateMachine.startReactively()).thenReturn(Mono.empty());
        when(stateMachine.stopReactively()).thenReturn(Mono.empty());
        when(stateMachine.getId()).thenReturn(PROJECT_ID);
        when(stateMachine.getStateMachineAccessor()).thenReturn(stateMachineAccessor);

        State<ProjectState, ProjectEvent> initial = mock(State.class);
        when(initial.getId()).thenReturn(ProjectState.PROJECT_INIT);
        when(stateMachine.getState()).thenReturn(initial);

        when(projectStateRepository.save(any(ProjectRuntime.class))).thenAnswer(inv -> {
            ProjectRuntime r = inv.getArgument(0);
            store.put(r.getProjectId(), r);
            return r;
        });
        when(projectStateRepository.findByProjectId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));

        when(orchestratorProperties.isStubEnabled()).thenReturn(true);
        when(orchestratorProperties.getStubProcessType()).thenReturn("MACHINING");
        when(orchestratorProperties.getRetryMaxAttempts()).thenReturn(3);
    }

    // =================================================================
    // onModuleResult
    // =================================================================

    @Nested
    @DisplayName("onModuleResult — результат от модуля")
    class ModuleResultTests {

        @Test
        @DisplayName("Успешный результат: переход по событию + resetAttempt (O-21)")
        void onModuleResult_success() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.IS_PART, Map.of("result", "ok"), true);

            verify(transitionHandler).resetAttempt(PROJECT_ID, ProjectEvent.IS_PART);
            verify(decisionLogService).log(eq(PROJECT_ID), eq("MODULE_RESULT_OK"), any(Map.class), anyString());
        }

        @Test
        @DisplayName("Неуспешный результат: запуск retry")
        void onModuleResult_failure_retries() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(1);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.NORMS_READY, Map.of("error", "timeout"), false);

            verify(moduleCommandProducer).retry(eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY), any());
            verify(decisionLogService).log(eq(PROJECT_ID), eq("MODULE_RESULT_FAIL"), any(Map.class), anyString());
        }

        @Test
        @DisplayName("Исчерпание попыток: moveToAwaitingDecision")
        void onModuleResult_failure_attemptsExhausted() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(3);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.NORMS_READY, Map.of("error", "timeout"), false);

            verify(transitionHandler).moveToAwaitingDecision(any(), eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY));
            verify(decisionLogService).log(eq(PROJECT_ID), eq("MODULE_FAILED"), any(Map.class), anyString());
        }

        @Test
        @DisplayName("attempt == MAX (3) -> awaitingDecision в payload (O-28)")
        void onModuleResult_failure_marksAwaitingDecision() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(3);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.NORMS_READY, Map.of("e", "x"), false);

            ArgumentCaptor<ProjectRuntime> captor = ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, atLeastOnce()).save(captor.capture());
            ProjectRuntime last = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(last.getPayload())
                    .containsEntry("awaitingDecision", true)
                    .containsEntry("failedEvent", "NORMS_READY")
                    .containsEntry("attempts", 3);
        }

        @Test
        @DisplayName("attempt == MAX -> no retry")
        void onModuleResult_attemptEqualsMax_goesToAwaitingDecision() {
            runtime.setState(ProjectState.NORMING);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(transitionHandler.incrementAttempt(eq(PROJECT_ID), any())).thenReturn(3);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.NORMS_READY, Map.of("err", "x"), false);

            verify(moduleCommandProducer, never()).retry(anyString(), any(), any());
            verify(transitionHandler).moveToAwaitingDecision(any(), eq(PROJECT_ID), eq(ProjectEvent.NORMS_READY));
        }
    }

    // =================================================================
    // Sequential
    // =================================================================

    @Nested
    @DisplayName("Sequential forging -> machining (сессия 32)")
    class SequentialTests {

        @Test
        @DisplayName("startProject 2 ТП -> tpType=FORGING, pendingProcessTypes=[MACHINING]")
        void startProject_multiTp_pendingPopulated() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName("Seq").requestedBy("ivanov")
                    .plannedProcessTypes(List.of("FORGING", "MACHINING")).build();

            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);
            orchestrationService.startProject(request);

            ArgumentCaptor<ProjectRuntime> captor = ArgumentCaptor.forClass(ProjectRuntime.class);
            verify(projectStateRepository, atLeast(1)).save(captor.capture());
            ProjectRuntime saved = captor.getAllValues().stream()
                    .filter(r -> r.getPayload() != null && r.getPayload().containsKey("pendingProcessTypes"))
                    .findFirst().orElseThrow();
            assertThat(saved.getPayload().get("tpType")).isEqualTo("FORGING");
            List<?> pending = (List<?>) saved.getPayload().get("pendingProcessTypes");
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0)).isEqualTo("MACHINING");
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

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY, new HashMap<>(), true);

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
        @DisplayName("onModuleResult sequential: MODULE_RESULT_OK для промежуточного ТП")
        void onModuleResult_sequential_logsModuleResultOk() {
            runtime.setState(ProjectState.PART_PROCESS_PLANNING);
            Map<String, Object> p = new HashMap<>();
            p.put("tpType", "FORGING");
            p.put("pendingProcessTypes", new java.util.ArrayList<>(List.of("MACHINING")));
            runtime.setPayload(p);
            store.put(PROJECT_ID, runtime);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY, new HashMap<>(), true);

            verify(decisionLogService).log(eq(PROJECT_ID), eq("MODULE_RESULT_OK"), any(Map.class), any());
            verify(decisionLogService).log(eq(PROJECT_ID), eq("SEQUENTIAL_NEXT"), any(Map.class), any());
        }

        @Test
        @DisplayName("PART_PROCESS_READY без pending -> legacy dispatchEvent")
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

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.PART_PROCESS_READY, new HashMap<>(), true);

            verify(stateMachine).sendEvent(any(org.springframework.messaging.Message.class));
            verify(decisionLogService, never()).log(eq(PROJECT_ID), eq("SEQUENTIAL_NEXT"), any(Map.class), any());
        }

        @Test
        @DisplayName("startProject 4 ТП -> OrchestrationException")
        void startProject_4Tp_throws() {
            OrchestrationRequest request = OrchestrationRequest.builder()
                    .projectName("Too many").requestedBy("ivanov")
                    .plannedProcessTypes(List.of("CASTING", "FORGING", "MACHINING", "CASTING")).build();
            assertThatThrownBy(() -> orchestrationService.startProject(request))
                    .isInstanceOf(OrchestrationException.class).hasMessageContaining("не более 3");
        }
    }

    // =================================================================
    // ExtraBranchTests
    // =================================================================

    @Nested
    @DisplayName("Дополнительные ветки dispatchEvent")
    class ExtraBranchTests {

        @Test
        @DisplayName("dispatchEvent: auto-state с nextAuto != null -> рекурсивный переход")
        void dispatchEvent_autoState_recurses() {
            runtime.setState(ProjectState.GEOMETRY_ANALYSIS);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            when(stateMachine.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);

            State<ProjectState, ProjectEvent> s1 = mock(State.class);
            when(s1.getId()).thenReturn(ProjectState.GEOMETRY_ANALYSIS);
            State<ProjectState, ProjectEvent> s2 = mock(State.class);
            when(s2.getId()).thenReturn(ProjectState.NORMING);
            when(stateMachine.getState()).thenReturn(s1, s2, s2);

            when(transitionHandler.isAutoState(ProjectState.GEOMETRY_ANALYSIS)).thenReturn(true);
            when(transitionHandler.nextAuto(ProjectState.GEOMETRY_ANALYSIS)).thenReturn(ProjectEvent.IS_PART);

            orchestrationService.onModuleResult(PROJECT_ID, ProjectEvent.IS_PART, Map.of(), true);

            verify(stateMachine, atLeast(2)).sendEvent(any(org.springframework.messaging.Message.class));
        }

        @Test
        @DisplayName("obtainMachine: повторный вызов возвращает тот же StateMachine (кэш)")
        void obtainMachine_cachesPerProject() {
            runtime.setState(ProjectState.PROJECT_INIT);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));
            orchestrationService.getSummary(PROJECT_ID);
            orchestrationService.getSummary(PROJECT_ID);
            verify(stateMachineFactory, org.mockito.Mockito.times(1)).getStateMachine(anyString());
        }
    }

    // =================================================================
    // getModel
    // =================================================================

    @Nested
    @DisplayName("getModel — информация о модели")
    class DiagnosticsTests {

        @Test
        @DisplayName("getModel: возвращает поля модели из payload, остальное — игнорирует")
        void getModel_returnsModelFields() {
            Map<String, Object> p = new HashMap<>();
            p.put("objectRef", "geo-1");
            p.put("material", "Steel 45");
            p.put("tpType", "MACHINING");
            p.put("tpSource", "STUB");
            p.put("stubCode", 1);
            p.put("otherKey", "ignored");
            runtime.setPayload(p);
            when(projectStateRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(runtime));

            Map<String, Object> model = orchestrationService.getModel(PROJECT_ID);

            assertThat(model).containsEntry("objectRef", "geo-1")
                    .containsEntry("material", "Steel 45")
                    .containsEntry("tpType", "MACHINING")
                    .containsEntry("stubCode", 1);
            assertThat(model).doesNotContainKey("otherKey");
        }
    }
}