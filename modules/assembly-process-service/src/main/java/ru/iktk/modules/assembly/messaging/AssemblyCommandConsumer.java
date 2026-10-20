package ru.iktk.modules.assembly.messaging;

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
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.service.AssemblyProcessService;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer команд оркестратора для проектирования ТП сборки.
 *
 * <p>Слушает {@code iktk.assembly-process-service.queue}. По команде:
 * <ol>
 *   <li>Разбирает JSON (projectId, payload.objectRef).</li>
 *   <li>Вызывает {@link AssemblyProcessService#designProcess(AssemblyRequest)}.</li>
 *   <li>Публикует результат в {@code iktk.results} с ключом
 *       {@code iktk.result.assembly-process-service} и событием
 *       {@code ASSEMBLY_PROCESS_READY}.</li>
 * </ol>
 */
@Component
public class AssemblyCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssemblyCommandConsumer.class);

    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String RESULT_ROUTING_KEY = "iktk.result.assembly-process-service";
    private static final String RESULT_EVENT = "ASSEMBLY_PROCESS_READY";
    private static final String MODULE_NAME = "assembly-process-service";

    private final AssemblyProcessService assemblyProcessService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AssemblyCommandConsumer(AssemblyProcessService assemblyProcessService,
                                   RabbitTemplate rabbitTemplate,
                                   ObjectMapper objectMapper) {
        this.assemblyProcessService = assemblyProcessService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.assembly-process-service.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("AssemblyCommandConsumer: command received, routingKey={}, correlationId={}",
                routingKey, correlationId);

        String projectId = null;
        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");

            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String objectRef = "default-object";
            String bomRef = null;
            if (payload != null) {
                if (payload.get("objectRef") != null) objectRef = String.valueOf(payload.get("objectRef"));
                if (payload.get("bomRef") != null)    bomRef    = String.valueOf(payload.get("bomRef"));
            }

            log.info("AssemblyCommandConsumer: processing projectId={}, objectRef={}, bomRef={}",
                    projectId, objectRef, bomRef);

            AssemblyRequest request = new AssemblyRequest();
            request.setProjectId(projectId);
            request.setObjectRef(objectRef);
            request.setBomRef(bomRef);

            AssemblyResponse response = assemblyProcessService.designProcess(request);

            log.info("AssemblyCommandConsumer: process designed, processId={}, stages={}, components={}",
                    response.getProcessId(), response.getStageCount(), response.getComponentCount());
            sendResult(projectId, response, correlationId, true, null);

        } catch (Exception e) {
            log.error("AssemblyCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, correlationId, false, e.getMessage());
        }
    }

    private void sendResult(String projectId, AssemblyResponse response, String correlationId,
                            boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("AssemblyCommandConsumer: projectId missing, cannot send result");
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
                payload.put("strategy", response.getStrategy());
                payload.put("stageCount", response.getStageCount());
                payload.put("componentCount", response.getComponentCount());
                if (response.getTotalTimeMin() != null) {
                    payload.put("totalTimeMin", response.getTotalTimeMin().toPlainString());
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
            log.info("AssemblyCommandConsumer: result sent, projectId={}, success={}", projectId, success);
        } catch (Exception e) {
            log.error("AssemblyCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}