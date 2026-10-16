package ru.iktk.orchestrator.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Консьюмер результатов от профильных модулей и сервисов ИКТК.
 *
 * <p>Назначение:
 * <ul>
 *   <li>приём результатов от модулей (casting, forging, machining, norming)
 *       и сервисов (bom-service, equipment-service, recommendation-service,
 *       assembly-process-service);</li>
 *   <li>маршрутизация результата в оркестратор
 *       ({@link OrchestrationService#onModuleResult});</li>
 *   <li>обработка ошибок модулей: nack, повтор, DLQ;</li>
 *   <li>журналирование каждого результата в {@link DecisionLogService}.</li>
 * </ul>
 *
 * <p>Обмен сообщениями:
 * <pre>
 *   exchange:   iktk.results          (topic)
 *   routingKey: iktk.result.&lt;module&gt;  (например, iktk.result.bom-service)
 *   queue:      iktk.orchestrator.results.queue
 * </pre>
 *
 * <p>Ожидаемые заголовки входящего сообщения:
 * <pre>
 *   projectId    — идентификатор проекта (обязательный)
 *   module       — модуль-отправитель
 *   state        — состояние проекта на момент формирования результата
 *   event        — событие для перевода автомата
 *   correlationId — сквозной идентификатор (совпадает с отправленным)
 *   success      — "true"/"false" (в заголовке или в теле)
 * </pre>
 *
 * <p>Тело сообщения — JSON:
 * <pre>
 *   {
 *     "projectId": "...",
 *     "event": "BOM_EXTRACTED",
 *     "success": true,
 *     "payload": { ... },
 *     "message": "результат обработки",
 *     "durationMs": 1234
 *   }
 * </pre>
 */
@Component
public class ModuleResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ModuleResultConsumer.class);

    private final OrchestrationService orchestrationService;
    private final DecisionLogService decisionLogService;
    private final ObjectMapper objectMapper;

    public ModuleResultConsumer(OrchestrationService orchestrationService,
                                DecisionLogService decisionLogService,
                                ObjectMapper objectMapper) {
        this.orchestrationService = orchestrationService;
        this.decisionLogService = decisionLogService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Основной обработчик
    // ------------------------------------------------------------------

    /**
     * Приём результата от модуля.
     *
     * <p>Метод вызывается Spring AMQP при появлении сообщения в очереди
     * {@code iktk.orchestrator.results.queue}. Подтверждение (ack)
     * отправляется только при успешной обработке; при ошибке — nack
     * с последующей отправкой в DLQ.
     *
     * @param message       сырое сообщение RabbitMQ
     * @param routingKey    ключ маршрутизации (например, iktk.result.bom-service)
     * @param correlationId сквозной идентификатор, установленный продюсером
     */
    @RabbitListener(
            queues = "${iktk.messaging.results-queue:iktk.orchestrator.results.queue}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onResult(Message message,
                         @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                         @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        MessageProperties props = message.getMessageProperties();

        String projectId = header(props, "projectId");
        String module = header(props, "module");
        String state = header(props, "state");
        String eventName = header(props, "event");
        String attempt = header(props, "attempt");
        String sentAt = header(props, "sentAt");

        log.info("onResult: routingKey={}, projectId={}, module={}, state={}, event={}, attempt={}",
                routingKey, projectId, module, state, eventName, attempt);

        try {
            JsonNode root = objectMapper.readTree(body);

            if (projectId == null) {
                projectId = text(root, "projectId");
            }
            if (eventName == null) {
                eventName = text(root, "event");
            }

            boolean success = resolveSuccess(root, props);
            Map<String, String> payload = flattenPayload(root);

            if (projectId == null || projectId.isBlank()) {
                log.error("onResult: отсутствует projectId в сообщении, routingKey={}", routingKey);
                decisionLogService.log("UNKNOWN", "MODULE_RESULT_REJECTED",
                        Map.of("reason", "MISSING_PROJECT_ID",
                                "routingKey", routingKey,
                                "body", truncate(body, 500)));
                throw new OrchestrationException("Отсутствует projectId в результате модуля");
            }

            if (eventName == null || eventName.isBlank()) {
                log.error("onResult: отсутствует event в сообщении, projectId={}", projectId);
                decisionLogService.log(projectId, "MODULE_RESULT_REJECTED",
                        Map.of("reason", "MISSING_EVENT",
                                "routingKey", routingKey,
                                "body", truncate(body, 500)));
                throw new OrchestrationException("Отсутствует event в результате модуля");
            }

            ProjectEvent event = ProjectEvent.valueOf(eventName);

            // Дополнительные метаданные в payload для журнала
            payload.put("module", module == null ? "unknown" : module);
            payload.put("routingKey", routingKey);
            payload.put("correlationId", correlationId == null ? "" : correlationId);
            payload.put("attempt", attempt == null ? "1" : attempt);
            payload.put("sentAt", sentAt == null ? "" : sentAt);
            payload.put("state", state == null ? "" : state);

            // Передаём результат в оркестратор
            orchestrationService.onModuleResult(projectId, event, payload, success);

            log.info("onResult done: projectId={}, event={}, success={}", projectId, event, success);

        } catch (OrchestrationException e) {
            log.error("onResult: ошибка оркестрации: routingKey={}, correlationId={}",
                    routingKey, correlationId, e);
            throw e;
        } catch (Exception e) {
            log.error("onResult: необработанная ошибка: routingKey={}, correlationId={}",
                    routingKey, correlationId, e);
            throw new OrchestrationException(
                    "Ошибка обработки результата модуля: " + routingKey, e);
        }
    }

    // ------------------------------------------------------------------
    // Обработчик ошибок (DLQ)
    // ------------------------------------------------------------------

    /**
     * Приём сообщений из очереди мёртвых писем (DLQ).
     */
    @RabbitListener(
            queues = "${iktk.messaging.dlq:iktk.orchestrator.results.dlq}",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onDeadLetter(Message message,
                             @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        MessageProperties props = message.getMessageProperties();

        String projectId = header(props, "projectId");
        String module = header(props, "module");
        String event = header(props, "event");

        log.error("onDeadLetter: routingKey={}, projectId={}, module={}, event={}, body={}",
                routingKey, projectId, module, event, truncate(body, 500));

        decisionLogService.log(projectId == null ? "UNKNOWN" : projectId,
                "MODULE_RESULT_DLQ",
                Map.of(
                        "routingKey", routingKey,
                        "module", module == null ? "unknown" : module,
                        "event", event == null ? "unknown" : event,
                        "body", truncate(body, 500)
                ),
                "SYSTEM");
    }

    // ------------------------------------------------------------------
    // Разбор тела сообщения
    // ------------------------------------------------------------------

    private boolean resolveSuccess(JsonNode root, MessageProperties props) {
        // Приоритет: заголовок, затем тело
        String headerSuccess = header(props, "success");
        if (headerSuccess != null) {
            return Boolean.parseBoolean(headerSuccess);
        }
        if (root != null && root.has("success")) {
            return root.get("success").asBoolean(true);
        }
        return true;
    }

    /**
     * Плоское представление payload (ключ → строка).
     * Вложенные объекты и массивы сериализуются в JSON-строку.
     */
    private Map<String, String> flattenPayload(JsonNode root) {
        Map<String, String> payload = new HashMap<>();
        if (root == null || !root.has("payload")) {
            return payload;
        }
        JsonNode payloadNode = root.get("payload");
        if (payloadNode.isObject()) {
            payloadNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                payload.put(entry.getKey(),
                        value.isValueNode() ? value.asText() : value.toString());
            });
        }
        return payload;
    }

    private String text(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private String header(MessageProperties props, String name) {
        if (props == null) {
            return null;
        }
        Object value = props.getHeader(name);
        return value == null ? null : value.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
