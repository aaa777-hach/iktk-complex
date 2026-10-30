
package ru.iktk.orchestrator.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.messaging.ModuleCommandProducer;
import ru.iktk.orchestrator.service.DecisionLogService;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Обработчик переходов конечного автомата оркестратора ИКТК.
 *
 * <p>Отвечает за:
 * <ul>
 *   <li>маршрутизацию управления профильным модулям и сервисам
 *       в зависимости от текущего состояния проекта;</li>
 *   <li>определение следующего автоматического события;</li>
 *   <li>определение события для точки контроля по команде approve;</li>
 *   <li>список доступных событий для текущего состояния;</li>
 *   <li>учёт повторных попыток при отказе внешнего модуля;</li>
 *   <li>перевод проекта в состояние «Ожидает решения» при исчерпании попыток.</li>
 * </ul>
 *
 * <p>Класс инкапсулирует всю «карту маршрутов»: соответствие
 * состояние → модуль/сервис, состояние → автоматическое событие,
 * состояние → событие approve.
 */
@Component
public class TransitionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransitionHandler.class);

    private final ModuleCommandProducer moduleCommandProducer;
    private final DecisionLogService decisionLogService;

    /** Счётчики попыток: projectId + event → число попыток. */
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    /**
     * Карта маршрутизации: состояние → модуль/сервис, которому передаётся управление.
     */
    private static final Map<ProjectState, String> ROUTES = new EnumMap<>(ProjectState.class);

    static {
        ROUTES.put(ProjectState.GEOMETRY_ANALYSIS,         "geometry-analysis");
        ROUTES.put(ProjectState.ASSEMBLY_ANALYSIS,         "bom-service");
        ROUTES.put(ProjectState.PART_PROCESS_PLANNING,     "casting");
        ROUTES.put(ProjectState.TOOLING_DESIGN,            "tooling-service");
        ROUTES.put(ProjectState.EQUIPMENT_MATCHING,        "equipment-service");
        ROUTES.put(ProjectState.EQUIPMENT_GAP_RESOLUTION,  "recommendation-service");
        ROUTES.put(ProjectState.ASSEMBLY_PROCESS_PLANNING, "assembly-process-service");
        ROUTES.put(ProjectState.NORMING,                   "norming");
        ROUTES.put(ProjectState.DOCUMENTATION,             "documentation-service");
        ROUTES.put(ProjectState.TECHNICAL_CONTROL,         "technical-control");
    }

    public TransitionHandler(ModuleCommandProducer moduleCommandProducer,
                             DecisionLogService decisionLogService) {
        this.moduleCommandProducer = moduleCommandProducer;
        this.decisionLogService = decisionLogService;
    }

    // ------------------------------------------------------------------
    // Маршрутизация управления
    // ------------------------------------------------------------------

    /**
     * Передать управление профильному модулю/сервису по текущему состоянию.
     *
     * <p>Для автоматических состояний отправляет команду в очередь.
     * Для точек контроля — только фиксирует в журнале: ожидается решение пользователя.
     *
     * @param projectId идентификатор проекта
     * @param state     текущее состояние
     * @param sm        активный конечный автомат проекта
     */
    public void route(String projectId,
                      ProjectState state,
                      StateMachine<ProjectState, ProjectEvent> sm,
                      Map<String, ?> payload) {
        String module = ROUTES.get(state);

        if (state == ProjectState.PART_PROCESS_PLANNING) {
            // O-19: без tpType или с неизвестным tpType — throw, не тихий fallback в casting.
            String tpType = payload == null ? null : String.valueOf(payload.get("tpType"));
            if (tpType == null || tpType.isBlank() || "null".equals(tpType)) {
                throw new OrchestrationException(
                        "PART_PROCESS_PLANNING: не указан tpType в payload (projectId=" + projectId + "). "
                                + "Допустимые: CASTING, FORGING, MACHINING");
            }
            String normalized = tpType.trim().toUpperCase(Locale.ROOT);
            String routed = switch (normalized) {
                case "CASTING"   -> "casting";
                case "FORGING"   -> "forging";
                case "MACHINING" -> "machining";
                default          -> null;
            };
            if (routed == null) {
                throw new OrchestrationException(
                        "PART_PROCESS_PLANNING: неизвестный tpType=" + tpType
                                + " (projectId=" + projectId + "). Допустимые: CASTING, FORGING, MACHINING");
            }
            module = routed;
            String source = payload.get("tpSource") != null
                    ? String.valueOf(payload.get("tpSource"))
                    : "USER";
            decisionLogService.log(projectId, "PART_PROCESS_PLANNING",
                    Map.of("tp", normalized, "source", source, "module", module),
                    "SYSTEM");
        }

        if (module == null) {
            if (state.isTerminal()) {
                log.info("route: projectId={}, state={} — терминальное состояние, маршрутизация не требуется",
                        projectId, state);
                return;
            }
            log.warn("route: projectId={}, state={} — модуль не определён", projectId, state);
            return;
        }

        if (state.isPointOfControl()
                && state != ProjectState.PART_PROCESS_PLANNING
                && state != ProjectState.ASSEMBLY_PROCESS_PLANNING
                && state != ProjectState.NORMING) {
            log.info("route: projectId={}, state={} — точка контроля, ожидается решение пользователя",
                    projectId, state);
            decisionLogService.log(projectId, "AWAITING_DECISION",
                    Map.of("state", state.name(), "module", module));
            return;
        }

        log.info("route: projectId={}, state={} → module={}", projectId, state, module);
        moduleCommandProducer.send(projectId, module, state, payload);

        decisionLogService.log(projectId, "ROUTED",
                Map.of("state", state.name(), "module", module));
    }

    // ------------------------------------------------------------------
    // Определение событий
    // ------------------------------------------------------------------

    /**
     * Следующее автоматическое событие для автоматического состояния.
     * Для точек контроля возвращает {@code null}.
     */
    public ProjectEvent nextAuto(ProjectState state) {
        return switch (state) {
            case PROJECT_INIT          -> ProjectEvent.START_GEOMETRY_INPUT;
            case GEOMETRY_INPUT        -> ProjectEvent.GEOMETRY_UPLOADED;
            case GEOMETRY_ANALYSIS     -> ProjectEvent.IS_PART;      // по умолчанию деталь
            case ASSEMBLY_ANALYSIS     -> null; // wait for external BOM_EXTRACTED from bom-service
            case EQUIPMENT_MATCHING    -> null; // wait for external EQUIPMENT_GAP (techdebt: equipment-service consumer)
            case EQUIPMENT_GAP_RESOLUTION -> null; // wait for external RECOMMENDATIONS_READY from recommendation-service
            case NORMING               -> null; // wait for external NORMS_READY from norming-module
            case DOCUMENTATION         -> ProjectEvent.DOCUMENTATION_READY;
            default -> null;
        };
    }

    /**
     * Событие, соответствующее подтверждению на точке контроля (approve).
     * Для автоматических состояний возвращает {@code nextAuto}.
     */
    public ProjectEvent nextOnApprove(ProjectState state) {
        return switch (state) {
            case PART_PROCESS_PLANNING     -> null; // wait for external PART_PROCESS_READY from casting
            case TOOLING_DESIGN            -> ProjectEvent.TOOLING_READY;
            case RECOMMENDATION_REVIEW     -> ProjectEvent.RECOMMENDATION_ACCEPTED;
            case ASSEMBLY_PROCESS_PLANNING -> ProjectEvent.ASSEMBLY_PROCESS_READY;
            case NORMING                   -> ProjectEvent.NORMS_READY;
            case TECHNICAL_CONTROL         -> ProjectEvent.CONTROL_PASSED;
            default -> nextAuto(state);
        };
    }

    /**
     * Проверка: является ли состояние автоматическим (без точки контроля).
     */
    public boolean isAutoState(ProjectState state) {
        return state != null && state.isAutomatic();
    }

    /**
     * Список доступных событий для текущего состояния (для UI и API).
     */
    public List<ProjectEvent> availableEvents(ProjectState state) {
        if (state == null) {
            return List.of();
        }

        return switch (state) {
            case GEOMETRY_ANALYSIS -> List.of(ProjectEvent.IS_PART, ProjectEvent.IS_ASSEMBLY);
            case EQUIPMENT_MATCHING -> List.of(ProjectEvent.EQUIPMENT_MATCHED, ProjectEvent.EQUIPMENT_GAP);
            case RECOMMENDATION_REVIEW -> List.of(
                    ProjectEvent.RECOMMENDATION_ACCEPTED,
                    ProjectEvent.PROCESS_ADJUSTED,
                    ProjectEvent.OUTSOURCING_SELECTED);
            case TECHNICAL_CONTROL -> List.of(
                    ProjectEvent.CONTROL_PASSED,
                    ProjectEvent.CONTROL_REJECTED);
            default -> {
                ProjectEvent auto = state.isAutomatic() ? nextAuto(state) : nextOnApprove(state);
                yield auto == null ? List.of() : List.of(auto);
            }
        };
    }

    // ------------------------------------------------------------------
    // Повторные попытки при отказе модуля
    // ------------------------------------------------------------------

    /**
     * Увеличить счётчик попыток для события проекта.
     *
     * @return текущее число попыток (начиная с 1)
     */
    public int incrementAttempt(String projectId, ProjectEvent event) {
        String key = projectId + ":" + event.name();
        int value = attempts
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();
        log.debug("incrementAttempt: key={}, attempt={}", key, value);
        return value;
    }

    /**
     * Сбросить счётчик попыток (при успехе).
     */
    public void resetAttempt(String projectId, ProjectEvent event) {
        String key = projectId + ":" + event.name();
        attempts.remove(key);
        log.debug("resetAttempt: key={}", key);
    }

    /**
     * Сбросить все счётчики по проекту.
     */
    public void resetAttempts(String projectId) {
        attempts.keySet().removeIf(key -> key.startsWith(projectId + ":"));
        log.debug("resetAttempts: projectId={}", projectId);
    }

    // ------------------------------------------------------------------
    // Ожидание решения пользователя
    // ------------------------------------------------------------------

    /**
     * Перевести проект в режим «Ожидает решения» после исчерпания попыток.
     *
     * <p>Технолог через UI может выбрать: повторить, заменить модуль,
     * скорректировать ТП или пропустить этап с пометкой.
     */
    public void moveToAwaitingDecision(StateMachine<ProjectState, ProjectEvent> sm,
                                       String projectId,
                                       ProjectEvent failedEvent) {
        ProjectState current = sm.getState().getId();

        log.warn("moveToAwaitingDecision: projectId={}, state={}, failedEvent={}",
                projectId, current, failedEvent);

        decisionLogService.log(projectId, "AWAITING_DECISION",
                Map.of(
                        "state", current.name(),
                        "failedEvent", failedEvent.name(),
                        "reason", "MODULE_RETRIES_EXHAUSTED"
                ),
                "SYSTEM"
        );
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /**
     * Проверка: есть ли маршрут для состояния.
     */
    public boolean hasRoute(ProjectState state) {
        return ROUTES.containsKey(state);
    }

    /**
     * Получить имя модуля для состояния.
     */
    public String moduleFor(ProjectState state) {
        return ROUTES.get(state);
    }

    /**
     * Множество состояний, для которых определены маршруты.
     */
    public Set<ProjectState> routedStates() {
        return ROUTES.keySet();
    }

    /**
     * Полная карта маршрутов (для диагностики и UI).
     */
    public Map<ProjectState, String> allRoutes() {
        return Map.copyOf(ROUTES);
    }

    /**
     * Проверка консистентности: каждое автоматическое и контрольное состояние,
     * кроме терминального, должно иметь маршрут.
     */
    public void validateRoutes() {
        for (ProjectState state : ProjectState.values()) {
            if (state.isTerminal()) {
                continue;
            }
            if (!ROUTES.containsKey(state)) {
                throw new OrchestrationException(
                        "Не определён маршрут для состояния: " + state);
            }
        }
        log.info("validateRoutes: все маршруты определены ({} состояний)", ROUTES.size());
    }
}