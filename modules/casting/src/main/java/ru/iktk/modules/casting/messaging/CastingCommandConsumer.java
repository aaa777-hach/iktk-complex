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

    public CastingCommandConsumer(CastingProcessService castingProcessService,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.castingProcessService = castingProcessService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.casting.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("CastingCommandConsumer: command received, routingKey={}, correlationId={}", routingKey, correlationId);

        String projectId = null;
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

            CastingResponse response = designWithFallback(request, objectRef);

            log.info("CastingCommandConsumer: process designed, processId={}, method={}",
                    response.getProcessId(), response.getMethod());
            sendResult(projectId, response, correlationId, true, null);

        } catch (Exception e) {
            log.error("CastingCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, correlationId, false, e.getMessage());
        }
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
        try {
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

            rabbitTemplate.convertAndSend(
                    "iktk.results",
                    "iktk.result.casting",
                    objectMapper.writeValueAsString(resultBody),
                    message -> {
                        MessageProperties props = message.getMessageProperties();
                        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        if (correlationId != null) props.setCorrelationId(correlationId);
                        props.setHeader("projectId", projectId);
                        props.setHeader("module", "casting");
                        props.setHeader("event", "PART_PROCESS_READY");
                        props.setHeader("success", success);
                        return message;
                    }
            );
            log.info("CastingCommandConsumer: result sent, projectId={}, success={}", projectId, success);
        } catch (Exception e) {
            log.error("CastingCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}