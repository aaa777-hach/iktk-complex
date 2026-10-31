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
import ru.iktk.orchestrator.config.OrchestratorProperties;
import ru.iktk.orchestrator.messaging.ModuleCommandProducer;
import ru.iktk.orchestrator.model.ProjectRuntime;
import ru.iktk.orchestrator.repository.ProjectStateRepository;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;
import ru.iktk.orchestrator.statemachine.TransitionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    /** Коды заглушки ТП: MACHINING → 1, CASTING → 2, FORGING → 3. */
    private static final Map<String, Integer> STUB_CODES = Map.of(
            "MACHINING", 1,
            "CASTING",   2,
            "FORGING",   3
    );

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory;
    private final ProjectStateRepository projectStateRepository;
    private final ModuleCommandProducer moduleCommandProducer;
    private final TransitionHandler transitionHandler;
    private final DecisionLogService decisionLogService;
    private final OrchestratorProperties properties;

    private final Map<String, StateMachine<ProjectState, ProjectEvent>> machines =
            new ConcurrentHashMap<>();

    public OrchestrationService(StateMachineFactory<ProjectState, ProjectEvent> stateMachineFactory,
                                ProjectStateRepository projectStateRepository,
                                ModuleCommandProducer moduleCommandProducer,
                                TransitionHandler transitionHandler,
                                DecisionLogService decisionLogService,
                                OrchestratorProperties properties) {
        this.stateMachineFactory = stateMachineFactory;
        this.projectStateRepository = projectStateRepository;
        this.moduleCommandProducer = moduleCommandProducer;
        this.transitionHandler = transitionHandler;
        this.decisionLogService = decisionLogService;
        this.properties = properties;
    }

    @Transactional
    public OrchestrationResponse startProject(OrchestrationRequest request) {
        validateRequest(request);

        String projectId = UUID.randomUUID().toString();
        log.info("startProject: projectId={}, name={}", projectId, request.getProjectName());

        // Начальный payload: objectRef, objectType (ASSEMBLY/PART), assemblyId
        Map<String, Object> initialPayload = new HashMap<>();
        if (request.getGeometryId() != null && !request.getGeometryId().isBlank()) {
            initialPayload.put("objectRef", request.getGeometryId());
        }
        if (request.getObjectType() != null) {
            initialPayload.put("objectType", request.getObjectType().name());
        }
        if (request.getAssemblyId() != null && !request.getAssemblyId().isBlank()) {
            initialPayload.put("assemblyId", request.getAssemblyId());
        }

        // Поля модели из ответа integration (POST /geometry/from-step)
        if (request.getMaterial() != null) {
            initialPayload.put("material", request.getMaterial());
        }
        if (request.getVolume() != null) {
            initialPayload.put("volume", request.getVolume());
        }
        if (request.getMass() != null) {
            initialPayload.put("mass", request.getMass());
        }
        if (request.getComplexity() != null) {
            initialPayload.put("complexity", request.getComplexity());
        }
        if (request.getWarnings() != null && !request.getWarnings().isEmpty()) {
            initialPayload.put("warnings", new ArrayList<>(request.getWarnings()));
        }

        // ===== TP =====
        boolean isAssembly = request.getObjectType() == ru.iktk.core.domain.Project.ObjectType.ASSEMBLY;
        List<String> tps = resolveAndValidateTps(request.getPlannedProcessTypes(), isAssembly);
        if (tps != null && !tps.isEmpty()) {
            // legacy-путь: явный plannedProcessTypes
            initialPayload.put("tpType", tps.get(0));
            initialPayload.put("tpSource", "USER");
            if (tps.size() > 1) {
                initialPayload.put("pendingProcessTypes", new ArrayList<>(tps.subList(1, tps.size())));
            }
            log.info("startProject: tpType={}, pending={}, objectType={}", tps.get(0), tps.size() - 1, request.getObjectType());
        } else if (!isAssembly) {
            // stub-путь: plannedProcessTypes не задан → заглушка
            applyStubProcessType(request, initialPayload);
        }

        ProjectRuntime runtime = ProjectRuntime.builder()
                .projectId(projectId)
                .projectName(request.getProjectName())
                .requestedBy(request.getRequestedBy())
                .payload(initialPayload)
                .state(ProjectState.PROJECT_INIT)
                .createdAt(Instant.now())
                .build();

        projectStateRepository.save(runtime);

        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, ProjectState.PROJECT_INIT);
        sm.startReactively().block();

        dispatchEvent(projectId, sm, ProjectEvent.START_GEOMETRY_INPUT, null);

        decisionLogService.log(projectId, "PROJECT_STARTED",
                Map.of("requestedBy", String.valueOf(request.getRequestedBy())), currentActor());

        return buildResponse(runtime, sm);
    }

    @Transactional
    public OrchestrationResponse run(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        log.info("run: projectId={}, currentState={}", projectId, runtime.getState());

        transitionHandler.route(projectId, runtime.getState(), sm, runtime.getPayload());

        decisionLogService.log(projectId, "ORCHESTRATION_RUN",
                Map.of("state", runtime.getState().name()), currentActor());

        return buildResponse(runtime, sm);
    }

    public ProjectState getState(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        return runtime.getState();
    }

    public OrchestrationResponse getSummary(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());
        return buildResponse(runtime, sm);
    }

    /**
     * Информация о модели проекта (задача 2: что записано в БД по модели).
     * Возвращает подмножество payload, относящееся к модели.
     */
    public Map<String, Object> getModel(String projectId) {
        ProjectRuntime runtime = loadRuntime(projectId);
        Map<String, Object> model = new LinkedHashMap<>();
        Map<String, Object> payload = runtime.getPayload();
        if (payload != null) {
            copyIfPresent(payload, model, "objectRef");
            copyIfPresent(payload, model, "objectType");
            copyIfPresent(payload, model, "assemblyId");
            copyIfPresent(payload, model, "material");
            copyIfPresent(payload, model, "volume");
            copyIfPresent(payload, model, "mass");
            copyIfPresent(payload, model, "complexity");
            copyIfPresent(payload, model, "warnings");
            copyIfPresent(payload, model, "tpType");
            copyIfPresent(payload, model, "tpSource");
            copyIfPresent(payload, model, "stubCode");
            copyIfPresent(payload, model, "pendingProcessTypes");
        }
        return model;
    }

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

    @Transactional
    public OrchestrationResponse sendEvent(String projectId,
                                           ProjectEvent event,
                                           Map<String, String> payload) {
        ProjectRuntime runtime = loadRuntime(projectId);
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        log.info("sendEvent: projectId={}, event={}, payload={}", projectId, event, payload);

        dispatchEvent(projectId, sm, event, payload);

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

    @Transactional
    public OrchestrationResponse rollback(String projectId, String version) {
        log.warn("rollback: projectId={}, version={}", projectId, version);

        ProjectRuntime runtime = projectStateRepository
                .findByProjectIdAndVersion(projectId, version)
                .orElseThrow(() -> new OrchestrationException(
                        "Версия не найдена: " + version + " для проекта " + projectId));

        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());
        resetMachine(sm, runtime.getState());

        decisionLogService.log(projectId, "ROLLBACK",
                Map.of("toVersion", version, "state", runtime.getState().name()), currentActor());

        return buildResponse(runtime, sm);
    }

    @Transactional
    public void onModuleResult(String projectId, ProjectEvent event,
                               Map<String, String> payload, boolean success) {
        log.info("onModuleResult: projectId={}, event={}, success={}", projectId, event, success);

        ProjectRuntime runtime = loadRuntime(projectId);

        // SEQUENTIAL: PART_PROCESS_READY с pendingProcessTypes -> route next ТП, state machine остаётся в PART_PROCESS_PLANNING
        if (success && event == ProjectEvent.PART_PROCESS_READY && hasPendingTypes(runtime)) {
            decisionLogService.log(projectId, "MODULE_RESULT_OK",
                    Map.of("event", event.name(), "module", String.valueOf(runtime.getPayload().get("tpType")), "mode", "SEQUENTIAL"), currentActor());
            handleSequentialNext(projectId, runtime, payload);
            return;
        }
        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());

        if (success) {
            // O-21: успешный результат сбрасывает счётчик retry
            transitionHandler.resetAttempt(projectId, event);
            dispatchEvent(projectId, sm, event, payload);
        } else {
            handleModuleFailure(projectId, sm, event, payload);
        }

        decisionLogService.log(projectId, success ? "MODULE_RESULT_OK" : "MODULE_RESULT_FAIL",
                Map.of("event", event.name(),
                        "payload", payload == null ? Map.of() : payload), currentActor());
    }

    /**
     * Проверка: в payload есть отложенные ТП (sequential forging -> machining).
     */
    private boolean hasPendingTypes(ProjectRuntime runtime) {
        if (runtime == null || runtime.getPayload() == null) return false;
        return asPendingList(runtime.getPayload().get("pendingProcessTypes")) != null;
    }

    // O-34: tolerant pendingProcessTypes parsing (List or JSON string).
    // Returns null when absent, empty, or unparsable.
    private List<?> asPendingList(Object p) {
        if (p instanceof List<?> l) {
            return l.isEmpty() ? null : l;
        }
        if (p instanceof String s && !s.isBlank()) {
            try {
                List<?> l = JSON.readValue(s, List.class);
                return (l == null || l.isEmpty()) ? null : l;
            } catch (Exception e) {
                log.warn("asPendingList: failed to parse pendingProcessTypes as JSON: {}", s);
                return null;
            }
        }
        return null;
    }

    /**
     * SEQUENTIAL: следующий ТП из pendingProcessTypes -> tpType, route напрямую,
     * state machine остаётся в PART_PROCESS_PLANNING (без sendEvent).
     */
    private void handleSequentialNext(String projectId, ProjectRuntime runtime, Map<String, String> payload) {
        List<?> pending = asPendingList(runtime.getPayload().get("pendingProcessTypes"));
        if (pending == null) {
            log.warn("handleSequentialNext: pendingProcessTypes empty/invalid, projectId={}", projectId);
            return;
        }
        String nextType = String.valueOf(pending.get(0));
        List<Object> remaining = new ArrayList<>(pending.subList(1, pending.size()));

        Map<String, Object> newPayload = new HashMap<>(runtime.getPayload());
        // O-34: merge consumer payload first; tpType/pendingProcessTypes below take precedence.
        if (payload != null) newPayload.putAll(payload);
        newPayload.put("tpType", nextType);
        newPayload.put("tpSource", "SEQUENTIAL");
        if (remaining.isEmpty()) {
            newPayload.remove("pendingProcessTypes");
        } else {
            newPayload.put("pendingProcessTypes", remaining);
        }

        runtime.setPayload(newPayload);
        projectStateRepository.save(runtime);

        StateMachine<ProjectState, ProjectEvent> sm = obtainMachine(projectId, runtime.getState());
        transitionHandler.route(projectId, runtime.getState(), sm, newPayload);

        decisionLogService.log(projectId, "SEQUENTIAL_NEXT",
                Map.of("nextTp", nextType, "remaining", String.valueOf(remaining.size())), currentActor());
        log.info("handleSequentialNext: projectId={}, nextTp={}, remaining={}", projectId, nextType, remaining.size());
    }

    private void handleModuleFailure(String projectId,
                                     StateMachine<ProjectState, ProjectEvent> sm,
                                     ProjectEvent event,
                                     Map<String, String> payload) {
        int attempt = transitionHandler.incrementAttempt(projectId, event);
        int maxAttempts = resolveMaxRetries();

        if (attempt < maxAttempts) {
            log.warn("Повторная попытка {} из {} для события {} (projectId={})",
                    attempt, maxAttempts, event, projectId);
            moduleCommandProducer.retry(projectId, event, payload);
            return;
        }

        log.error("Модуль исчерпал попытки: projectId={}, event={}", projectId, event);

        // O-28: фиксируем «ожидает решения» в payload (runtime.state не меняем — SM остаётся)
        markAwaitingDecision(projectId, event, attempt);

        decisionLogService.log(projectId, "MODULE_FAILED",
                Map.of("event", event.name(), "attempts", String.valueOf(attempt)), currentActor());

        transitionHandler.moveToAwaitingDecision(sm, projectId, event);
    }

    private void markAwaitingDecision(String projectId, ProjectEvent event, int attempt) {
        ProjectRuntime rt = loadRuntime(projectId);
        Map<String, Object> merged = new HashMap<>();
        if (rt.getPayload() != null) {
            merged.putAll(rt.getPayload());
        }
        merged.put("awaitingDecision", true);
        merged.put("failedEvent", event.name());
        merged.put("attempts", attempt);
        rt.setPayload(merged);
        projectStateRepository.save(rt);
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
        // Мерж payload в runtime: артефакты от модулей (processId, bomRef
        // и т.п.) должны доходить до следующего route(). run() берёт payload
        // из runtime.getPayload() — без этого шага он был бы null.
        if (payload != null && !payload.isEmpty()) {
            ProjectRuntime rt = loadRuntime(projectId);
            Map<String, Object> merged = new HashMap<>();
            if (rt.getPayload() != null) {
                merged.putAll(rt.getPayload());
            }
            // O-34: pendingProcessTypes and tpType are owned by startProject/handleSequentialNext.
            // Do not merge them from consumer payload (arrays arrive as JSON strings there).
            for (Map.Entry<String, String> e : payload.entrySet()) {
                String k = e.getKey();
                if ("pendingProcessTypes".equals(k) || "tpType".equals(k)) {
                    continue;
                }
                merged.put(k, e.getValue());
            }
            rt.setPayload(merged);
            projectStateRepository.save(rt);
        }

        persistState(projectId, sm, newState);

        // O-18: убран дублирующий вызов route() — оставлен только рекурсивный
        // dispatchEvent для автоматических состояний.
        if (transitionHandler.isAutoState(newState)) {
            ProjectEvent autoEvent = transitionHandler.nextAuto(newState);

            // Спецслучай: GEOMETRY_ANALYSIS + objectType=ASSEMBLY -> IS_ASSEMBLY
            if (newState == ProjectState.GEOMETRY_ANALYSIS
                    && autoEvent == ProjectEvent.IS_PART) {
                ProjectRuntime rt = loadRuntime(projectId);
                if (rt.getPayload() != null
                        && "ASSEMBLY".equals(String.valueOf(rt.getPayload().get("objectType")))) {
                    autoEvent = ProjectEvent.IS_ASSEMBLY;
                    log.info("dispatchEvent: GEOMETRY_ANALYSIS + objectType=ASSEMBLY -> IS_ASSEMBLY, projectId={}",
                            projectId);
                }
            }

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
        Map<String, Object> payload = runtime.getPayload();
        Boolean awaitingDecision = payload != null && Boolean.TRUE.equals(payload.get("awaitingDecision"));
        String failedEvent = payload == null ? null : str(payload.get("failedEvent"));
        Integer attempts = payload == null ? null : intOrNull(payload.get("attempts"));

        return OrchestrationResponse.builder()
                .projectId(runtime.getProjectId())
                .projectName(runtime.getProjectName())
                .state(state.name())
                .stateDisplayName(state.getDisplayName())
                .stateCategory(state.getCategory().name())
                .pointOfControl(state.isPointOfControl())
                .awaitingUserAction(state.isPointOfControl() || Boolean.TRUE.equals(awaitingDecision))
                .availableEvents(transitionHandler.availableEvents(state))
                .awaitingDecision(awaitingDecision)
                .failedEvent(failedEvent)
                .attempts(attempts)
                .model(extractModel(payload))
                .updatedAt(runtime.getUpdatedAt())
                .build();
    }

    private Map<String, Object> extractModel(Map<String, Object> payload) {
        if (payload == null) return null;
        Map<String, Object> model = new LinkedHashMap<>();
        copyIfPresent(payload, model, "objectRef");
        copyIfPresent(payload, model, "objectType");
        copyIfPresent(payload, model, "material");
        copyIfPresent(payload, model, "volume");
        copyIfPresent(payload, model, "mass");
        copyIfPresent(payload, model, "complexity");
        copyIfPresent(payload, model, "warnings");
        copyIfPresent(payload, model, "tpType");
        copyIfPresent(payload, model, "tpSource");
        copyIfPresent(payload, model, "stubCode");
        return model.isEmpty() ? null : model;
    }

    private void copyIfPresent(Map<String, Object> src, Map<String, Object> dst, String key) {
        if (src.containsKey(key)) {
            dst.put(key, src.get(key));
        }
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer intOrNull(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Заглушка выбора ТП (на первом этапе).
     * Берёт {@code request.stubProcessType} или {@code properties.stubProcessType},
     * нормализует, кладёт tpType/tpSource=STUB/stubCode в payload.
     */
    private void applyStubProcessType(OrchestrationRequest request, Map<String, Object> initialPayload) {
        if (!properties.isStubEnabled()) {
            throw new OrchestrationException(
                    "Укажите ТП изготовления детали: plannedProcessTypes не передан "
                            + "(заглушка выбора ТП отключена: iktk.orchestrator.stub-enabled=false)");
        }
        String raw = request.hasStubProcessType()
                ? request.getStubProcessType()
                : properties.getStubProcessType();
        if (raw == null || raw.isBlank()) {
            throw new OrchestrationException(
                    "Заглушка ТП: не задан stubProcessType (ни в запросе, ни в конфиге)");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        Integer code = STUB_CODES.get(normalized);
        if (code == null) {
            throw new OrchestrationException(
                    "Заглушка ТП: неизвестный ТП " + raw + ". Допустимые: MACHINING (1), CASTING (2), FORGING (3)");
        }
        initialPayload.put("tpType", normalized);
        initialPayload.put("tpSource", "STUB");
        initialPayload.put("stubCode", code);
        log.info("startProject: stub ТП -> tpType={}, stubCode={}", normalized, code);
    }

    private int resolveMaxRetries() {
        int v = properties.getRetryMaxAttempts();
        return v > 0 ? v : 3;
    }

    /**
     * SEQUENTIAL: нормализованный список ТП (1..3). Для сборки — null.
     * Если plannedProcessTypes пуст — возвращает null (дальше решает stub/throw).
     */
    private List<String> resolveAndValidateTps(List<String> plannedProcessTypes, boolean isAssembly) {
        if (isAssembly) {
            if (plannedProcessTypes != null && !plannedProcessTypes.isEmpty()) {
                log.warn("startProject: objectType=ASSEMBLY, plannedProcessTypes={} ignored", plannedProcessTypes);
            }
            return null;
        }
        if (plannedProcessTypes == null || plannedProcessTypes.isEmpty()) {
            // stub-ветка решит: либо заглушка, либо throw (если stubEnabled=false)
            return null;
        }
        if (plannedProcessTypes.size() > 3) {
            throw new OrchestrationException(
                    "Поддерживается не более 3 ТП на деталь; передано " + plannedProcessTypes.size());
        }
        List<String> result = new ArrayList<>();
        for (String tp : plannedProcessTypes) {
            if (tp == null || tp.isBlank()) {
                throw new OrchestrationException(
                        "plannedProcessTypes содержит пустое значение; допустимые: CASTING, FORGING, MACHINING");
            }
            String normalized = tp.trim().toUpperCase(Locale.ROOT);
            switch (normalized) {
                case "CASTING", "FORGING", "MACHINING" -> result.add(normalized);
                default -> throw new OrchestrationException(
                        "Неизвестный ТП: " + tp + ". Допустимые: CASTING, FORGING, MACHINING");
            }
        }
        return result;
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
        Map<String, String> result = new HashMap<>();
        if (payload != null) {
            result.putAll(payload);
        }
        result.put(key, value);
        return result;
    }

    public List<String> activeProjects() {
        return List.copyOf(machines.keySet());
    }

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
        }
        return "SYSTEM";
    }
}