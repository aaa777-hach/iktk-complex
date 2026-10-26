package ru.iktk.modules.forging.messaging;

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
import ru.iktk.modules.forging.dto.ForgingRequest;
import ru.iktk.modules.forging.dto.ForgingResponse;
import ru.iktk.modules.forging.service.ForgingProcessService;

import java.util.HashMap;
import java.util.Map;

@Component
public class ForgingCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(ForgingCommandConsumer.class);

    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String RESULT_ROUTING_KEY = "iktk.result.forging";
    private static final String RESULT_EVENT = "PART_PROCESS_READY";
    private static final String MODULE_NAME = "forging";

    private final ForgingProcessService forgingProcessService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ForgingCommandConsumer(ForgingProcessService forgingProcessService,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.forgingProcessService = forgingProcessService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.forging.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("ForgingCommandConsumer: command received, routingKey={}, correlationId={}",
                routingKey, correlationId);

        String projectId = null;
        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String objectRef = payload != null && payload.get("objectRef") != null
                    ? String.valueOf(payload.get("objectRef"))
                    : "default-object";

            log.info("ForgingCommandConsumer: processing projectId={}, objectRef={}",
                    projectId, objectRef);

            ForgingRequest request = new ForgingRequest();
            request.setProjectId(projectId);
            request.setObjectRef(objectRef);

            ForgingResponse response = forgingProcessService.designProcess(request);

            log.info("ForgingCommandConsumer: process designed, processId={}, method={}, transitions={}",
                    response.getProcessId(), response.getMethod(),
                    response.getTransitions() != null ? response.getTransitions().size() : 0);
            sendResult(projectId, response, correlationId, true, null);

        } catch (Exception e) {
            log.error("ForgingCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, correlationId, false, e.getMessage());
        }
    }

    private void sendResult(String projectId, ForgingResponse response, String correlationId,
                            boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("ForgingCommandConsumer: projectId missing, cannot send result");
            return;
        }
        try {
            Map<String, Object> resultBody = new HashMap<>();
            resultBody.put("projectId", projectId);
            resultBody.put("event", RESULT_EVENT);
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
            log.info("ForgingCommandConsumer: result sent, projectId={}, success={}", projectId, success);
        } catch (Exception e) {
            log.error("ForgingCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}
