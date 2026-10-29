package ru.iktk.modules.casting.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.casting.dto.CastingRequest;
import ru.iktk.modules.casting.dto.CastingResponse;
import ru.iktk.modules.casting.service.CastingProcessService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class CastingCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(CastingCommandConsumer.class);

    private final CastingProcessService castingProcessService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${iktk.casting.demo-fallback:false}")
    private boolean demoFallback;

    @Value("${iktk.casting.result-exchange:iktk.results}")
    private String resultExchange;

    @Value("${iktk.casting.result-routing-key:iktk.result.casting}")
    private String resultRoutingKey;

    @Value("${iktk.casting.result-send-max-attempts:3}")
    private int resultSendMaxAttempts;

    @Value("${iktk.casting.result-send-backoff-ms:500}")
    private long resultSendBackoffMs;

    public CastingCommandConsumer(CastingProcessService castingProcessService,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.castingProcessService = castingProcessService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.casting.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String incomingRoutingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("CastingCommandConsumer: command received, routingKey={}, correlationId={}",
                incomingRoutingKey, correlationId);

        String projectId = null;
        CastingResponse response = null;
        boolean success = true;
        String errorMessage = null;

        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");

            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String objectRef = "default-object";
            if (payload != null && payload.get("objectRef") != null) {
                objectRef = String.valueOf(payload.get("objectRef"));
            }

            log.info("CastingCommandConsumer: processing projectId={}, objectRef={}", projectId, objectRef);

            CastingRequest request = new CastingRequest();
            request.setProjectId(projectId);
            request.setObjectRef(objectRef);

            response = designWithFallback(request, objectRef);

            log.info("CastingCommandConsumer: process designed, processId={}, method={}",
                    response.getProcessId(), response.getMethod());
        } catch (Exception e) {
            log.error("CastingCommandConsumer: processing error, projectId={}", projectId, e);
            success = false;
            errorMessage = e.getMessage();
        }

        // sendResult бросает OrchestrationException при исчерпании попыток →
        // listener не ACK'ает → default-requeue-rejected=false → discard/DLQ.
        sendResult(projectId, response, correlationId, success, errorMessage);
    }

    private CastingResponse designWithFallback(CastingRequest request, String objectRef) {
        try {
            return castingProcessService.designProcess(request);
        } catch (Exception e) {
            if (!demoFallback) {
                log.error("CastingCommandConsumer: geometry fetch failed, demo-fallback disabled: {}", e.getMessage());
                throw e;
            }
            log.warn("CastingCommandConsumer: geometry fetch failed ({}), fallback to DEMO geometry", e.getMessage());
            CastingProcessService.GeometryData demo = new CastingProcessService.GeometryData(
                    objectRef, "Steel 45",
                    BigDecimal.valueOf(1000), BigDecimal.valueOf(7.8),
                    BigDecimal.valueOf(5), BigDecimal.valueOf(3),
                    BigDecimal.valueOf(1.5), BigDecimal.valueOf(100),
                    3, false, false
            );
            return castingProcessService.designProcessWithGeometry(request, demo);
        }
    }

    private void sendResult(String projectId, CastingResponse response, String correlationId,
                            boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("CastingCommandConsumer: projectId missing, cannot send result");
            return;
        }

        Map<String, Object> resultBody = new HashMap<>();
        resultBody.put("projectId", projectId);
        resultBody.put("event", "PART_PROCESS_READY");
        resultBody.put("success", success);

        Map<String, Object> payload = new HashMap<>();
        if (success && response != null) {
            payload.put("processId", response.getProcessId());
            payload.put("method", response.getMethod() == null ? null : response.getMethod().name());
        } else {
            payload.put("error", errorMessage == null ? "unknown" : errorMessage);
        }
        resultBody.put("payload", payload);

        final String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(resultBody);
        } catch (Exception e) {
            throw new OrchestrationException("Не удалось сериализовать PART_PROCESS_READY: " + e.getMessage());
        }

        final boolean finalSuccess = success;
        final int maxAttempts = Math.max(1, resultSendMaxAttempts);
        final long backoffMs = Math.max(0L, resultSendBackoffMs);
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rabbitTemplate.convertAndSend(resultExchange, resultRoutingKey, bodyJson, message -> {
                    MessageProperties props = message.getMessageProperties();
                    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    if (correlationId != null) props.setCorrelationId(correlationId);
                    props.setHeader("projectId", projectId);
                    props.setHeader("module", "casting");
                    props.setHeader("event", "PART_PROCESS_READY");
                    props.setHeader("success", finalSuccess);
                    return message;
                });
                log.info("CastingCommandConsumer: result sent, projectId={}, success={}, attempt={}/{}",
                        projectId, success, attempt, maxAttempts);
                return;
            } catch (Exception e) {
                lastFailure = e;
                log.warn("CastingCommandConsumer: result send attempt {}/{} failed: {}",
                        attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("CastingCommandConsumer: result send FAILED after {} attempts, projectId={}",
                maxAttempts, projectId);
        throw new OrchestrationException(
                "Не удалось отправить PART_PROCESS_READY после " + resultSendMaxAttempts +
                " попыток: " + (lastFailure == null ? "unknown" : lastFailure.getMessage()));
    }
}