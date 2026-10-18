package ru.iktk.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной сервис оркестрации ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>создание проекта и инициализацию конечного автомата;</li>
 *   <li>анализ текущего состояния и выбор следующего этапа ТПП;</li>
 *   <li>передачу управления профильным модулям и сервисам через очереди;</li>
 *   <li>обработку результатов и событий от модулей;</li>
 *   <li>поддержку точек контроля (approve, adjust, outsource);</li>
 *   <li>откат к предыдущей версии/состоянию;</li>
 *   <li>журналирование всех решений через {@link DecisionLogService}.</li>
 * </ul>
 *
 * <p>Режим — смешанный: автоматические переходы выполняются сразу,
 * точки контроля ожидают внешнего события (approve / sendEvent).
 */
@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    /** Политика повторных попыток при отказе внешнего модуля. */
    private static final int MAX_RETRIES = 3;

    private final StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory;
    private final ProjectStateRepository projectStateRepository;
    private final ModuleCommandProducer moduleCommandProducer;
    private final TransitionHandler transitionHandler;
    private final DecisionLogService decisionLogService;

    /**
     * Реестр активных автоматов: projectId → StateMachine.
     * Для горизонтального масштабирования состояние восстанавливается из БД.
     */
    private final Map<String, StateMachine<ProjectState, ProjectEvent>> machines =
            new ConcurrentHashMap<>();

    public OrchestrationService(StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory,
                                ProjectStateRepository projectStateRepository,
                                ModuleCommandProducer moduleCommandProducer,
                                TransitionHandler transitionHandler,
                                DecisionLogService decisionLogService) {
        this.stateMachineFactory = stateMachineFactory;
        this.projectStateRepository = projectStateRepository;
        this.moduleCommandProducer = moduleCommandProducer;
        this.transitionHandler = transitionHandler;
        this.decisionLogService = decisionLogService;
    }

    // ------------------------------------------------------------------
    // Создание и запуск проекта
    // ------------------------------------------------------------------

    /**
     * Создать новый проект и запустить оркестрацию.
     */
    @Transactional
    public OrchestrationResponse startProject(OrchestrationRequest request) {
        validateRequest(request);

        String projectId = UUID.randomUUID().toString();
        log.info("startProject: projectId={}, name={}", projectId, request.getProjectName());

        ProjectRuntime runtime = ProjectRuntime.builder()
                .projectId(projectId)
                .projectName(request.getProjectName())
                .requestedBy(request.getRequestedBy())
                .payload(request.getGeometryId() != null && !request.getGeometryId().isBlank()
                        ? Map.of("objectRef", request.getGeometryId())
                        : Map.of())
                .state(ProjectState.PROJECT_INIT)
                .createdAt(Instant.now())
                .build();

        projectStateRepository.save(runtime);

        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, ProjectState.PROJECT_INIT);
        sm.startReactively().block();

        // Автоматический переход: PROJECT_INIT → GEOMETRY_INPUT
        dispatchEvent(projectId, sm, ProjectEvent.START_GEOMETRY_INPUT, null);

        decisionLogService.log(projectId, "PROJECT_STARTED",
                Map.of("requestedBy", request.getRequestedBy()), currentActor());

        return buildResponse(runtime, sm);
    }

    /**
     * Продолжить оркестрацию: определить следующий этап и передать управление.
     */
    @Transactional
    public OrchestrationResponse run(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        log.info("run: projectId={}, currentState={}", projectId, runtime.getState());

        // Передать управление профильному модулю по текущему состоянию
        transitionHandler.route(projectId, runtime.getState(), sm, runtime.getPayload());

        decisionLogService.log(projectId, "ORCHESTRATION_RUN",
                Map.of("state", runtime.getState().name()), currentActor());

        return buildResponse(runtime, sm);
    }

    // ------------------------------------------------------------------
    // Состояние и сводка
    // ------------------------------------------------------------------

    public ProjectState getState(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        return runtime.getState();
    }

    public OrchestrationResponse getSummary(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());
        return buildResponse(runtime, sm);
    }

    // ------------------------------------------------------------------
    // Точки контроля
    // ------------------------------------------------------------------

    /**
     * Подтверждение текущего этапа: перевод на следующее автоматическое состояние.
     */
    @Transactional
    public OrchestrationResponse approve(String projectId, String comment) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        ProjectState current = sm.getState().getId();
        ProjectEvent autoEvent = transitionHandler.nextOnApprove(current);

        log.info("approve: projectId={}, state={}, event={}", projectId, current, autoEvent);

        if (autoEvent == null) {
            throw new OrchestrationException(
                    "Для состояния " + current + " подтверждение не предусмотрено");
        }

        dispatchEvent(projectId, sm, autoEvent, comment == null ? null : Map.of("comment", comment));

        decisionLogService.log(projectId, "APPROVED",
                Map.of("fromState", current.name(),
                        "event", autoEvent.name(),
                        "comment", comment == null ? "" : comment), currentActor());

        return buildResponse(loadRuntime(projectId), sm);
    }

    /**
     * Отправить внешнее событие в автомат проекта.
     */
    @Transactional
    public OrchestrationResponse sendEvent(String projectId,
                                           ProjectEvent event,
                                           Map<String, String> payload) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        log.info("sendEvent: projectId={}, event={}, payload={}", projectId, event, payload);

        if (!sm.sendEvent(org.springframework.messaging.support.MessageBuilder
                .withPayload(event)
                .setHeader("projectId", projectId)
                .setHeader("payload", payload)
                .build())) {
            throw new OrchestrationException(
                    "Событие " + event + " не принято в состоянии " + sm.getState().getId());
        }

        decisionLogService.log(projectId, "EVENT_SENT",
                Map.of("event", event.name(),
                        "payload", payload == null ? Map.of() : payload), currentActor());

        return buildResponse(loadRuntime(projectId), sm);
    }

    @Transactional
    public OrchestrationResponse acceptRecommendation(String projectId,
                                                      String recommendationId,
                                                      Map<String, String> payload) {
        log.info("acceptRecommendation: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return sendEvent(projectId, ProjectEvent.RECOMMENDATION_ACCEPTED,
                merge(payload, "recommendationId", recommendationId));
    }

    @Transactional
    public OrchestrationResponse adjustProcess(String projectId,
                                               String recommendationId,
                                               Map<String, String> payload) {
        log.info("adjustProcess: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return sendEvent(projectId, ProjectEvent.PROCESS_ADJUSTED,
                merge(payload, "recommendationId", recommendationId));
    }

    @Transactional
    public OrchestrationResponse selectOutsourcing(String projectId,
                                                   String recommendationId,
                                                   Map<String, String> payload) {
        log.info("selectOutsourcing: projectId={}, recommendationId={}",
                projectId, recommendationId);
        return sendEvent(projectId, ProjectEvent.OUTSOURCING_SELECTED,
                merge(payload, "recommendationId", recommendationId));
    }

    // ------------------------------------------------------------------
    // Откат
    // ------------------------------------------------------------------

    @Transactional
    public OrchestrationResponse rollback(String projectId, String version) {
        log.warn("rollback: projectId={}, version={}", projectId, version);

        ProjectRuntime runtime = projectStateRepository
                .findByProjectIdAndVersion(projectId, version)
                .orElseThrow(() -> new OrchestrationException(
                        "Версия не найдена: " + version + " для проекта " + projectId));

        // Сбрасываем автомат в сохранённое состояние
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());
        resetMachine(sm, runtime.getState());

        decisionLogService.log(projectId, "ROLLBACK",
                Map.of("toVersion", version, "state", runtime.getState().name()), currentActor());

        return buildResponse(runtime, sm);
    }

    // ------------------------------------------------------------------
    // Обработка результатов от модулей (вызывается из Consumer)
    // ------------------------------------------------------------------

    /**
     * Обработать результат, пришедший от внешнего модуля или сервиса.
     */
    @Transactional
    public void onModuleResult(String projectId, ProjectEvent event,
                               Map<String, String> payload, boolean success) {
        log.info("onModuleResult: projectId={}, event={}, success={}", projectId, event, success);

        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        if (success) {
            dispatchEvent(projectId, sm, event, payload);
        } else {
            handleModuleFailure(projectId, sm, event, payload);
        }

        decisionLogService.log(projectId, success ? "MODULE_RESULT_OK" : "MODULE_RESULT_FAIL",
                Map.of("event", event.name(),
                        "payload", payload == null ? Map.of() : payload), currentActor());
    }

    // ------------------------------------------------------------------
    // Внутренние методы
    // ------------------------------------------------------------------

    private void handleModuleFailure(String projectId,
                                     StateMachine<ProjectState, ProjectEvent> sm,
                                     ProjectEvent event,
                                     Map<String, String> payload) {
        int attempt = transitionHandler.incrementAttempt(projectId, event);

        if (attempt < MAX_RETRIES) {
            log.warn("Повторная попытка {} для события {} (projectId={})",
                    attempt, event, projectId);
            moduleCommandProducer.retry(projectId, event, payload);
            return;
        }

        log.error("Модуль исчерпал попытки: projectId={}, event={}", projectId, event);
        decisionLogService.log(projectId, "MODULE_FAILED",
                Map.of("event", event.name(), "attempts", String.valueOf(attempt)), currentActor());

        // Состояние «Ожидает решения» — точка контроля для инженера
        transitionHandler.moveToAwaitingDecision(sm, projectId, event);
    }

    private void dispatchEvent(String projectId, StateMachine<ProjectState, ProjectEvent> sm,
                               ProjectEvent event,
                               Map<String, String> payload) {
        boolean accepted = sm.sendEvent(org.springframework.messaging.support.MessageBuilder
                .withPayload(event)
                .setHeader("payload", payload)
                .build());

        if (!accepted) {
            ProjectState current = sm.getState().getId();
            log.warn("Событие {} не принято в состоянии {}", event, current);
            throw new OrchestrationException(
                    "Событие " + event + " не принято в состоянии " + current);
        }

        ProjectState newState = sm.getState().getId();
        persistState(projectId, sm, newState);

        // Автоматический следующий переход, если это не точка контроля
        if (transitionHandler.isAutoState(newState)) {
            ProjectEvent autoEvent = transitionHandler.nextAuto(newState);
            if (autoEvent != null) {
                dispatchEvent(projectId, sm, autoEvent, null);
            }
        }
    }

    private StateMachine<ProjectState, ProjectEvent> obtainMachine(String projectId,
                                                                   ProjectState state) {
        return machines.computeIfAbsent(projectId, id -> {
            StateMachine<ProjectState, ProjectEvent> sm = stateMachineFactory.getStateMachine(id);
            resetMachine(sm, state);
            sm.startReactively().block();
            return sm;
        });
    }

    private void resetMachine(StateMachine<ProjectState, ProjectEvent> sm, ProjectState state) {
        sm.stopReactively().block();
        sm.getStateMachineAccessor().doWithAllRegions(access ->
                access.resetStateMachineReactively(
                        new DefaultStateMachineContext<>(state, null, null, null)).block());
        sm.startReactively().block();
    }

    private void persistState(String projectId, StateMachine<ProjectState, ProjectEvent> sm, ProjectState state) {
        ProjectRuntime runtime = loadRuntime(projectId);
        runtime.setState(state);
        runtime.setUpdatedAt(Instant.now());
        projectStateRepository.save(runtime);
    }

    private ProjectRuntime loadRuntime(String projectId) {
        return projectStateRepository.findByProjectId(projectId)
                .orElseThrow(() -> new OrchestrationException(
                        "Проект не найден: " + projectId));
    }

    private OrchestrationResponse buildResponse(ProjectRuntime runtime,
                                                StateMachine<ProjectState, ProjectEvent> sm) {
        ProjectState state = sm.getState().getId();
        return OrchestrationResponse.builder()
                .projectId(runtime.getProjectId())
                .projectName(runtime.getProjectName())
                .state(state.name())
                .stateDisplayName(state.getDisplayName())
                .stateCategory(state.getCategory().name())
                .pointOfControl(state.isPointOfControl())
                .awaitingUserAction(state.isPointOfControl())
                .availableEvents(transitionHandler.availableEvents(state))
                .updatedAt(runtime.getUpdatedAt())
                .build();
    }

    private void validateRequest(OrchestrationRequest request) {
        if (request == null) {
            throw new OrchestrationException("Запрос на создание проекта пуст");
        }
        if (request.getProjectName() == null || request.getProjectName().isBlank()) {
            throw new OrchestrationException("Не указано имя проекта");
        }
    }

    private Map<String, String> merge(Map<String, String> payload,
                                      String key, String value) {
        Map<String, String> result = new java.util.HashMap<>();
        if (payload != null) {
            result.putAll(payload);
        }
        result.put(key, value);
        return result;
    }

    /** Для диагностики. */
    public List<String> activeProjects() {
        return List.copyOf(machines.keySet());
    }
    /**
     * Получить actor из текущего HTTP-запроса (заголовок X-User-Id, который подставляет gateway).
     * Для вызовов вне HTTP-контекста (например, из RabbitMQ-консьюмера) — возвращает "SYSTEM".
     */
    private String currentActor() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String userId = attrs.getRequest().getHeader("X-User-Id");
                if (userId != null && !userId.isBlank()) {
                    return userId;
                }
            }
        } catch (Exception ignored) {
            // вне HTTP-запроса
        }
        return "SYSTEM";
    }
}
