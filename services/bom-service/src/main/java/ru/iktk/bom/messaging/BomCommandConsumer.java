package ru.iktk.bom.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.service.BomService;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer команд оркестратора для анализа BOM.
 *
 * <p>Слушает {@code iktk.bom-service.queue}. По команде:
 * <ol>
 *   <li>Разбирает JSON (projectId, payload.geometryId, payload.cadSystem).</li>
 *   <li>Вызывает {@link BomService#analyze(BomAnalyzeRequest)}.</li>
 *   <li>Публикует результат в {@code iktk.results} с ключом
 *       {@code iktk.result.bom-service} и событием {@code BOM_EXTRACTED}.</li>
 * </ol>
 */
@Component
public class BomCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(BomCommandConsumer.class);

    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String RESULT_ROUTING_KEY = "iktk.result.bom-service";
    private static final String RESULT_EVENT = "BOM_EXTRACTED";
    private static final String MODULE_NAME = "bom-service";

    private final BomService bomService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public BomCommandConsumer(BomService bomService,
                              RabbitTemplate rabbitTemplate,
                              ObjectMapper objectMapper) {
        this.bomService = bomService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.bom-service.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("BomCommandConsumer: command received, routingKey={}, correlationId={}",
                routingKey, correlationId);

        String projectId = null;
        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");

            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String geometryId = null;
            String cadSystem = null;
            if (payload != null) {
                if (payload.get("geometryId") != null) geometryId = String.valueOf(payload.get("geometryId"));
                if (payload.get("cadSystem") != null)  cadSystem  = String.valueOf(payload.get("cadSystem"));
            }

            log.info("BomCommandConsumer: processing projectId={}, geometryId={}, cadSystem={}",
                    projectId, geometryId, cadSystem);

            BomAnalyzeRequest request = BomAnalyzeRequest.forProject(projectId);
            request.setGeometryId(geometryId);
            request.setCadSystem(cadSystem);

            BomResponse response = bomService.analyze(request);

            log.info("BomCommandConsumer: analysis done, projectId={}, status={}, positions={}",
                    projectId, response.getStatus(), response.getPositionsCount());
            sendResult(projectId, response, correlationId, true, null);

        } catch (Exception e) {
            log.error("BomCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, correlationId, false, e.getMessage());
        }
    }

    private void sendResult(String projectId, BomResponse response, String correlationId,
                            boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("BomCommandConsumer: projectId missing, cannot send result");
            return;
        }
        try {
            Map<String, Object> resultBody = new HashMap<>();
            resultBody.put("projectId", projectId);
            resultBody.put("event", RESULT_EVENT);
            resultBody.put("success", success);

            Map<String, Object> payload = new HashMap<>();
            if (success && response != null) {
                payload.put("assemblyId", response.getAssemblyId());
                payload.put("bomId", response.getBomId());
                payload.put("status", response.getStatus());
                if (response.getPositionsCount() != null) {
                    payload.put("positionsCount", String.valueOf(response.getPositionsCount()));
                }
                if (response.getConnectionsCount() != null) {
                    payload.put("connectionsCount", String.valueOf(response.getConnectionsCount()));
                }
            } else {
                payload.put("error", errorMessage == null ? "unknown" : errorMessage);
            }
            resultBody.put("payload", payload);

            rabbitTemplate.convertAndSend(
                    RESULTS_EXCHANGE,
                    RESULT_ROUTING_KEY,
                    objectMapper.writeValueAsString(resultBody),
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        if (correlationId != null) props.setCorrelationId(correlationId);
                        props.setHeader("projectId", projectId);
                        props.setHeader("module", MODULE_NAME);
                        props.setHeader("event", RESULT_EVENT);
                        props.setHeader("success", success);
                        return message;
                    }
            );
            log.info("BomCommandConsumer: result sent, projectId={}, success={}", projectId, success);
        } catch (Exception e) {
            log.error("BomCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}