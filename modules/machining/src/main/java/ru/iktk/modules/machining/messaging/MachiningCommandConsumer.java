package ru.iktk.modules.machining.messaging;

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
import ru.iktk.modules.machining.dto.CncProgramRequest;
import ru.iktk.modules.machining.dto.CncProgramResponse;
import ru.iktk.modules.machining.dto.MachiningRequest;
import ru.iktk.modules.machining.dto.MachiningResponse;
import ru.iktk.modules.machining.service.CncProgramService;
import ru.iktk.modules.machining.service.MachiningProcessService;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumer команд оркестратора для модуля механообработки.
 *
 * <p>Вход: {@code iktk.machining.queue} (routing key {@code iktk.command.machining}).
 * Выход: {@code iktk.results} (routing key {@code iktk.result.machining}, event {@code PART_PROCESS_READY}).
 *
 * <p>Сценарий:
 * <ol>
 *   <li>{@code designProcess} — проектирование ТП (machining_process).</li>
 *   <li>Если {@code requiresCncProgram=true} — {@code generate} — генерация УП (cnc_program + MinIO).</li>
 *   <li>Результат в {@code iktk.results}.</li>
 * </ol>
 */
@Component
public class MachiningCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(MachiningCommandConsumer.class);

    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String RESULT_ROUTING_KEY = "iktk.result.machining";
    private static final String RESULT_EVENT = "PART_PROCESS_READY";
    private static final String MODULE_NAME = "machining";

    private final MachiningProcessService machiningProcessService;
    private final CncProgramService cncProgramService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public MachiningCommandConsumer(MachiningProcessService machiningProcessService,
                                    CncProgramService cncProgramService,
                                    RabbitTemplate rabbitTemplate,
                                    ObjectMapper objectMapper) {
        this.machiningProcessService = machiningProcessService;
        this.cncProgramService = cncProgramService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.machining.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("MachiningCommandConsumer: command received, routingKey={}, correlationId={}",
                routingKey, correlationId);

        String projectId = null;
        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String objectRef = payload != null && payload.get("objectRef") != null
                    ? String.valueOf(payload.get("objectRef"))
                    : "default-object";

            log.info("MachiningCommandConsumer: processing projectId={}, objectRef={}",
                    projectId, objectRef);

            // 1. Проектирование ТП
            MachiningRequest request = MachiningRequest.builder()
                    .projectId(projectId)
                    .objectRef(objectRef)
                    .build();
            MachiningResponse response = machiningProcessService.designProcess(request);

            int operations = response.getOperations() == null ? 0 : response.getOperations().size();
            log.info("MachiningCommandConsumer: process designed, processId={}, operations={}, requiresCnc={}",
                    response.getProcessId(), operations, response.isRequiresCncProgram());

            // 2. Генерация УП (если требуется)
            String cncProgramId = null;
            String cncProgramUri = null;
            if (response.isRequiresCncProgram()) {
                CncProgramRequest cncReq = CncProgramRequest.builder()
                        .projectId(projectId)
                        .processId(response.getProcessId())
                        .operations(response.getOperations())
                        .build();
                CncProgramResponse cncResp = cncProgramService.generate(cncReq);
                cncProgramId = cncResp.getProgramId();
                cncProgramUri = cncResp.getProgramUri();
                log.info("MachiningCommandConsumer: cnc program generated, programId={}, uri={}",
                        cncProgramId, cncProgramUri);
            }

            // 3. Результат
            sendResult(projectId, response, cncProgramId, cncProgramUri, correlationId, true, null);

        } catch (Exception e) {
            log.error("MachiningCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, null, null, correlationId, false, e.getMessage());
        }
    }

    private void sendResult(String projectId, MachiningResponse response,
                            String cncProgramId, String cncProgramUri,
                            String correlationId, boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("MachiningCommandConsumer: projectId missing, cannot send result");
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
                payload.put("requiresCncProgram", response.isRequiresCncProgram());
                payload.put("operations", response.getOperations() == null
                        ? 0 : response.getOperations().size());
                payload.put("stages", response.getStages() == null
                        ? 0 : response.getStages().size());
                if (cncProgramId != null) {
                    payload.put("cncProgramId", cncProgramId);
                    payload.put("cncProgramUri", cncProgramUri);
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
            log.info("MachiningCommandConsumer: result sent, projectId={}, success={}, cncProgramId={}",
                    projectId, success, cncProgramId);
        } catch (Exception e) {
            log.error("MachiningCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}