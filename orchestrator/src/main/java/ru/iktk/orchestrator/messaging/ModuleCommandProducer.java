package ru.iktk.orchestrator.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Продюсер команд оркестратора к профильным модулям и сервисам ИКТК.
 *
 * <p>Назначение:
 * <ul>
 *   <li>отправка команд модулям через очередь (RabbitMQ по умолчанию);</li>
 *   <li>гарантированная доставка через подтверждения (publisher confirms);</li>
 *   <li>трассировка через заголовки сообщения (projectId, module, state, correlationId);</li>
 *   <li>поддержка повторных попыток (retry) при отказе модуля;</li>
 *   <li>фиксация факта отправки в журнале решений {@link DecisionLogService}.</li>
 * </ul>
 *
 * <p>Обмен сообщениями:
 * <pre>
 *   exchange:   iktk.commands            (topic)
 *   routingKey: iktk.command.&lt;module&gt;    (например, iktk.command.bom-service)
 *   queue:      iktk.&lt;module&gt;.queue      (например, iktk.bom-service.queue)
 * </pre>
 *
 * <p>Заголовки сообщения:
 * <pre>
 *   projectId      — идентификатор проекта
 *   module         — целевой модуль/сервис
 *   state          — состояние проекта на момент отправки
 *   event          — событие (для retry)
 *   correlationId  — сквозной идентификатор (совпадает с projectId + UUID)
 *   attempt        — номер попытки (1 для первичной отправки)
 *   sentAt         — ISO-8601 timestamp отправки
 * </pre>
 */
@Component
public class ModuleCommandProducer {

    private static final Logger log = LoggerFactory.getLogger(ModuleCommandProducer.class);

    /** Имя обменника команд. */
    @Value("${iktk.messaging.commands-exchange:iktk.commands}")
    private String commandsExchange;

    /** Префикс routing key: iktk.command.<module>. */
    @Value("${iktk.messaging.routing-prefix:iktk.command}")
    private String routingPrefix;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final DecisionLogService decisionLogService;

    public ModuleCommandProducer(RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper,
                                 DecisionLogService decisionLogService) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.decisionLogService = decisionLogService;
    }

    // ------------------------------------------------------------------
    // Основная отправка
    // ------------------------------------------------------------------

    /**
     * Отправить команду модулю по текущему состоянию проекта.
     *
     * @param projectId идентификатор проекта
     * @param module    целевой модуль/сервис (например, "bom-service")
     * @param state     состояние проекта
     * @param payload   дополнительные данные (может быть {@code null})
     */
    public void send(String projectId, String module, ProjectState state, Map<String, ?> payload) {
        validate(projectId, module, state);

        String routingKey = routingPrefix + "." + module;
        String correlationId = projectId + ":" + UUID.randomUUID();
        Map<String, Object> body = buildBody(projectId, module, state, null, payload, 1);

        try {
            rabbitTemplate.convertAndSend(
                    commandsExchange,
                    routingKey,
                    objectMapper.writeValueAsString(body),
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        props.setCorrelationId(correlationId);
                        props.setHeader("projectId", projectId);
                        props.setHeader("module", module);
                        props.setHeader("state", state.name());
                        props.setHeader("attempt", 1);
                        props.setHeader("sentAt", Instant.now().toString());
                        return message;
                    }
            );

            log.info("send: projectId={}, module={}, state={}, routingKey={}, correlationId={}",
                    projectId, module, state, routingKey, correlationId);

            decisionLogService.log(projectId, "COMMAND_SENT",
                    Map.of(
                            "module", module,
                            "state", state.name(),
                            "correlationId", correlationId,
                            "attempt", "1"
                    ));

        } catch (JsonProcessingException e) {
            log.error("send: ошибка сериализации тела команды: projectId={}, module={}",
                    projectId, module, e);
            throw new OrchestrationException(
                    "Ошибка сериализации команды для модуля " + module, e);
        } catch (Exception e) {
            log.error("send: ошибка отправки команды: projectId={}, module={}",
                    projectId, module, e);
            throw new OrchestrationException(
                    "Ошибка отправки команды модулю " + module, e);
        }
    }

    // ------------------------------------------------------------------
    // Повторная отправка (retry)
    // ------------------------------------------------------------------

    /**
     * Повторная отправка команды при отказе модуля.
     *
     * <p>Отличается от {@link #send} тем, что:
     * <ul>
     *   <li>использует исходное событие ({@code event}) как причину;</li>
     *   <li>увеличивает заголовок {@code attempt};</li>
     *   <li>пишет в журнал событие {@code COMMAND_RETRY}.</li>
     * </ul>
     *
     * @param projectId идентификатор проекта
     * @param event     исходное событие, по которому произошёл отказ
     * @param payload   дополнительные данные
     */
    public void retry(String projectId, ProjectEvent event, Map<String, ?> payload) {
        validate(projectId, event);

        String module = extractModuleFromPayload(payload);
        if (module == null) {
            module = moduleFor(event);
        }
        if (module == null) {
            log.error("retry: module не определён, event={}, projectId={}", event, projectId);
            throw new OrchestrationException(
                    "retry: module не определён для события " + event + " (projectId=" + projectId + ")");
        }

        final String resolvedModule = module;
        int attempt = extractAttempt(payload) + 1;
        String routingKey = routingPrefix + "." + module;
        String correlationId = projectId + ":" + UUID.randomUUID();
        Map<String, Object> body = buildBody(projectId, module, null, event, payload, attempt);

        try {
            rabbitTemplate.convertAndSend(
                    commandsExchange,
                    routingKey,
                    objectMapper.writeValueAsString(body),
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        props.setCorrelationId(correlationId);
                        props.setHeader("projectId", projectId);
                        props.setHeader("module", resolvedModule);
                        props.setHeader("event", event.name());
                        props.setHeader("attempt", attempt);
                        props.setHeader("sentAt", Instant.now().toString());
                        return message;
                    }
            );

            log.warn("retry: projectId={}, event={}, module={}, attempt={}, correlationId={}",
                    projectId, event, module, attempt, correlationId);

            decisionLogService.log(projectId, "COMMAND_RETRY",
                    Map.of(
                            "module", module,
                            "event", event.name(),
                            "attempt", String.valueOf(attempt),
                            "correlationId", correlationId
                    ));

        } catch (JsonProcessingException e) {
            log.error("retry: ошибка сериализации: projectId={}, event={}", projectId, event, e);
            throw new OrchestrationException("Ошибка сериализации retry-команды", e);
        } catch (Exception e) {
            log.error("retry: ошибка отправки: projectId={}, event={}", projectId, event, e);
            throw new OrchestrationException("Ошибка отправки retry-команды", e);
        }
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private Map<String, Object> buildBody(String projectId,
                                          String module,
                                          ProjectState state,
                                          ProjectEvent event,
                                          Map<String, ?> payload,
                                          int attempt) {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", projectId);
        body.put("module", module);
        body.put("attempt", attempt);
        body.put("sentAt", Instant.now().toString());
        if (state != null) {
            body.put("state", state.name());
        }
        if (event != null) {
            body.put("event", event.name());
        }
        if (payload != null && !payload.isEmpty()) {
            body.put("payload", payload);
        }
        return body;
    }

    private int extractAttempt(Map<String, ?> payload) {
        if (payload == null) {
            return 0;
        }
        Object raw = payload.get("attempt");
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Соответствие события → модуль.
     * Используется при retry, когда нет явного состояния.
     */
    private String extractModuleFromPayload(Map<String, ?> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get("module");
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return (s.isEmpty() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    private String moduleFor(ProjectEvent event) {
        return switch (event) {
            case START_GEOMETRY_INPUT, GEOMETRY_UPLOADED -> "geometry-analysis";
            case IS_PART                              -> "part-process-service";
            case PART_PROCESS_READY                   -> null; // C.8: module берётся из payload.module
            case IS_ASSEMBLY, BOM_EXTRACTED              -> "bom-service";
            case TOOLING_READY                           -> "tooling-service";
            case EQUIPMENT_MATCHED, EQUIPMENT_GAP        -> "equipment-service";
            case RECOMMENDATIONS_READY                   -> "recommendation-service";
            case RECOMMENDATION_ACCEPTED,
                 PROCESS_ADJUSTED,
                 OUTSOURCING_SELECTED,
                 ASSEMBLY_PROCESS_READY                  -> "assembly-process-service";
            case NORMS_READY                             -> "norming";
            case DOCUMENTATION_READY                     -> "documentation-service";
            case CONTROL_PASSED, CONTROL_REJECTED        -> "technical-control";
        };
    }

    private void validate(String projectId, String module, ProjectState state) {
        if (projectId == null || projectId.isBlank()) {
            throw new OrchestrationException("Не указан projectId для отправки команды");
        }
        if (module == null || module.isBlank()) {
            throw new OrchestrationException("Не указан module для отправки команды");
        }
        if (state == null) {
            throw new OrchestrationException("Не указано state для отправки команды");
        }
    }

    private void validate(String projectId, ProjectEvent event) {
        if (projectId == null || projectId.isBlank()) {
            throw new OrchestrationException("Не указан projectId для retry-команды");
        }
        if (event == null) {
            throw new OrchestrationException("Не указано event для retry-команды");
        }
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /** Имя обменника команд. */
    public String getCommandsExchange() {
        return commandsExchange;
    }

    /** Префикс routing key. */
    public String getRoutingPrefix() {
        return routingPrefix;
    }
}
