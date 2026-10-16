package ru.iktk.core.exception;

import java.io.Serial;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Базовое исключение оркестратора и модулей ИКТК.
 *
 * <p>Используется для сигнализации об ошибках бизнес-логики в:
 * <ul>
 *   <li>{@code OrchestrationService} — некорректные переходы, отсутствие проекта;</li>
 *   <li>{@code TransitionHandler} — отсутствие маршрута, недопустимое событие;</li>
 *   <li>{@code ModuleCommandProducer} — ошибки отправки команд;</li>
 *   <li>{@code ModuleResultConsumer} — некорректные результаты модулей;</li>
 *   <li>{@code DecisionLogService} — ошибки валидации журнала.</li>
 * </ul>
 *
 * <p>Отличается от {@link RuntimeException} тем, что:
 * <ul>
 *   <li>несёт <b>машиночитаемый код</b> ошибки ({@code code}) — для обработки
 *       на стороне клиента и в UI;</li>
 *   <li>может содержать <b>контекст</b> — набор key/value пар с деталями
 *       (например, {@code projectId}, {@code state}, {@code event});</li>
 *   <li>поддерживает <b>цепочку причин</b> ({@code cause}).</li>
 * </ul>
 *
 * <p>Примеры использования:
 * <pre>
 *   throw new OrchestrationException("Проект не найден: " + projectId);
 *
 *   throw new OrchestrationException(
 *       ErrorCode.PROJECT_NOT_FOUND,
 *       "Проект не найден",
 *       Map.of("projectId", projectId));
 *
 *   throw new OrchestrationException(
 *       ErrorCode.MODULE_SEND_FAILED,
 *       "Ошибка отправки команды модулю",
 *       Map.of("module", module, "state", state.name()),
 *       cause);
 * </pre>
 *
 * <p>В {@code OrchestratorController} обрабатывается {@code @ExceptionHandler}
 * и преобразуется в HTTP 422 с телом:
 * <pre>
 *   {
 *     "error": "ORCHESTRATION_ERROR",
 *     "code": "PROJECT_NOT_FOUND",
 *     "message": "Проект не найден: 8f3e...",
 *     "context": { "projectId": "8f3e..." }
 *   }
 * </pre>
 */
public class OrchestrationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Код ошибки по умолчанию — общая ошибка оркестрации. */
    public static final String DEFAULT_CODE = "ORCHESTRATION_ERROR";

    /** Машиночитаемый код ошибки. */
    private final String code;

    /** Контекст ошибки: key/value пары с деталями. */
    private final Map<String, Object> context;

    // ------------------------------------------------------------------
    // Конструкторы
    // ------------------------------------------------------------------

    /**
     * Создать исключение с сообщением и кодом по умолчанию.
     *
     * @param message описание ошибки
     */
    public OrchestrationException(String message) {
        this(DEFAULT_CODE, message, null, null);
    }

    /**
     * Создать исключение с сообщением и причиной.
     *
     * @param message описание ошибки
     * @param cause   исходная причина
     */
    public OrchestrationException(String message, Throwable cause) {
        this(DEFAULT_CODE, message, null, cause);
    }

    /**
     * Создать исключение с кодом, сообщением и контекстом.
     *
     * @param code    машиночитаемый код ошибки
     * @param message описание ошибки
     * @param context контекст (может быть {@code null})
     */
    public OrchestrationException(String code, String message, Map<String, Object> context) {
        this(code, message, context, null);
    }

    /**
     * Создать исключение с кодом, сообщением, контекстом и причиной.
     *
     * @param code    машиночитаемый код ошибки
     * @param message описание ошибки
     * @param context контекст (может быть {@code null})
     * @param cause   исходная причина (может быть {@code null})
     */
    public OrchestrationException(String code,
                                  String message,
                                  Map<String, Object> context,
                                  Throwable cause) {
        super(message, cause);
        this.code = (code == null || code.isBlank()) ? DEFAULT_CODE : code;
        this.context = context == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(context));
    }

    // ------------------------------------------------------------------
    // Фабричные методы (удобные константы ситуаций)
    // ------------------------------------------------------------------

    /**
     * Проект не найден.
     */
    public static OrchestrationException projectNotFound(String projectId) {
        return new OrchestrationException(
                ErrorCode.PROJECT_NOT_FOUND,
                "Проект не найден: " + projectId,
                Map.of("projectId", projectId));
    }

    /**
     * Недопустимый переход в конечном автомате.
     */
    public static OrchestrationException invalidTransition(String state, String event) {
        return new OrchestrationException(
                ErrorCode.INVALID_TRANSITION,
                "Недопустимый переход: state=" + state + ", event=" + event,
                Map.of("state", state, "event", event));
    }

    /**
     * Событие не принято автоматом в текущем состоянии.
     */
    public static OrchestrationException eventNotAccepted(String state, String event) {
        return new OrchestrationException(
                ErrorCode.EVENT_NOT_ACCEPTED,
                "Событие " + event + " не принято в состоянии " + state,
                Map.of("state", state, "event", event));
    }

    /**
     * Маршрут для состояния не определён.
     */
    public static OrchestrationException routeMissing(String state) {
        return new OrchestrationException(
                ErrorCode.ROUTE_MISSING,
                "Не определён маршрут для состояния: " + state,
                Map.of("state", state));
    }

    /**
     * Ошибка отправки команды модулю.
     */
    public static OrchestrationException moduleSendFailed(String module,
                                                          String projectId,
                                                          Throwable cause) {
        return new OrchestrationException(
                ErrorCode.MODULE_SEND_FAILED,
                "Ошибка отправки команды модулю " + module,
                Map.of("module", module, "projectId", projectId),
                cause);
    }

    /**
     * Некорректный результат от модуля.
     */
    public static OrchestrationException invalidModuleResult(String projectId,
                                                             String reason) {
        return new OrchestrationException(
                ErrorCode.INVALID_MODULE_RESULT,
                "Некорректный результат модуля: " + reason,
                Map.of("projectId", projectId, "reason", reason));
    }

    /**
     * Модуль исчерпал попытки.
     */
    public static OrchestrationException moduleRetriesExhausted(String module,
                                                                String event,
                                                                int attempts) {
        return new OrchestrationException(
                ErrorCode.MODULE_RETRIES_EXHAUSTED,
                "Модуль " + module + " исчерпал попытки: event=" + event + ", attempts=" + attempts,
                Map.of("module", module, "event", event, "attempts", attempts));
    }

    /**
     * Ошибка валидации входных данных.
     */
    public static OrchestrationException validationFailed(String field, String reason) {
        return new OrchestrationException(
                ErrorCode.VALIDATION_FAILED,
                "Ошибка валидации поля '" + field + "': " + reason,
                Map.of("field", field, "reason", reason));
    }

    /**
     * Версия не найдена (при откате).
     */
    public static OrchestrationException versionNotFound(String projectId, String version) {
        return new OrchestrationException(
                ErrorCode.VERSION_NOT_FOUND,
                "Версия не найдена: " + version + " для проекта " + projectId,
                Map.of("projectId", projectId, "version", version));
    }

    // ------------------------------------------------------------------
    // Геттеры
    // ------------------------------------------------------------------

    /**
     * Машиночитаемый код ошибки.
     */
    public String getCode() {
        return code;
    }

    /**
     * Контекст ошибки (иммутабельная карта).
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * Есть ли у ошибки контекст.
     */
    public boolean hasContext() {
        return !context.isEmpty();
    }

    // ------------------------------------------------------------------
    // toString
    // ------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OrchestrationException{");
        sb.append("code='").append(code).append('\'');
        sb.append(", message='").append(getMessage()).append('\'');
        if (!context.isEmpty()) {
            sb.append(", context=").append(context);
        }
        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getClass().getSimpleName());
        }
        sb.append('}');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Вложенный класс: коды ошибок
    // ------------------------------------------------------------------

    /**
     * Машиночитаемые коды ошибок оркестратора и модулей ИКТК.
     *
     * <p>Используются на стороне клиента и в UI для обработки конкретных
     * ситуаций без парсинга сообщения.
     */
    public static final class ErrorCode {

        /** Общая ошибка оркестрации. */
        public static final String ORCHESTRATION_ERROR = "ORCHESTRATION_ERROR";

        /** Проект не найден. */
        public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";

        /** Недопустимый переход в конечном автомате. */
        public static final String INVALID_TRANSITION = "INVALID_TRANSITION";

        /** Событие не принято автоматом в текущем состоянии. */
        public static final String EVENT_NOT_ACCEPTED = "EVENT_NOT_ACCEPTED";

        /** Маршрут для состояния не определён. */
        public static final String ROUTE_MISSING = "ROUTE_MISSING";

        /** Ошибка отправки команды модулю. */
        public static final String MODULE_SEND_FAILED = "MODULE_SEND_FAILED";

        /** Некорректный результат от модуля. */
        public static final String INVALID_MODULE_RESULT = "INVALID_MODULE_RESULT";

        /** Модуль исчерпал попытки. */
        public static final String MODULE_RETRIES_EXHAUSTED = "MODULE_RETRIES_EXHAUSTED";

        /** Ошибка валидации входных данных. */
        public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

        /** Версия не найдена при откате. */
        public static final String VERSION_NOT_FOUND = "VERSION_NOT_FOUND";

        /** Точка контроля: подтверждение не предусмотрено. */
        public static final String APPROVE_NOT_ALLOWED = "APPROVE_NOT_ALLOWED";

        /** Дефицит оборудования: рекомендации не сформированы. */
        public static final String RECOMMENDATIONS_MISSING = "RECOMMENDATIONS_MISSING";

        /** Ошибка интеграции с внешним CAD/CAM. */
        public static final String CAD_INTEGRATION_FAILED = "CAD_INTEGRATION_FAILED";

        /** Ошибка извлечения BOM. */
        public static final String BOM_EXTRACTION_FAILED = "BOM_EXTRACTION_FAILED";

        /** Ошибка доступа (RBAC/ABAC). */
        public static final String ACCESS_DENIED = "ACCESS_DENIED";

        private ErrorCode() {
            // константы
        }
    }
}