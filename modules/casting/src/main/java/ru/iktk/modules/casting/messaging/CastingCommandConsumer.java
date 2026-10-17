package ru.iktk.modules.casting.messaging;

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
        
        log.info("CastingCommandConsumer: получена команда, routingKey={}, correlationId={}", routingKey, correlationId);

        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            String projectId = (String) body.get("projectId");
            
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String objectRef = "default-object";
            if (payload != null && payload.containsKey("objectRef")) {
                objectRef = (String) payload.get("objectRef");
            }

            log.info("CastingCommandConsumer: обработка проекта projectId={}, objectRef={}", projectId, objectRef);

            CastingRequest request = new CastingRequest();
            request.setProjectId(projectId);
            request.setObjectRef(objectRef);

            CastingProcessService.GeometryData geometry = new CastingProcessService.GeometryData(
                    objectRef, "Сталь 45",
                    BigDecimal.valueOf(1000), BigDecimal.valueOf(7.8),
                    BigDecimal.valueOf(5), BigDecimal.valueOf(3),
                    BigDecimal.valueOf(1.5), BigDecimal.valueOf(100),
                    3, false, false
            );

            CastingResponse response = castingProcessService.designProcessWithGeometry(request, geometry);

            log.info("CastingCommandConsumer: процесс литья спроектирован, processId={}", response.getProcessId());

            sendResult(projectId, response, correlationId);

        } catch (Exception e) {
            log.error("CastingCommandConsumer: ошибка обработки команды", e);
        }
    }

    private void sendResult(String projectId, CastingResponse response, String correlationId) {
        try {
            Map<String, Object> resultBody = new HashMap<>();
            resultBody.put("projectId", projectId);
            resultBody.put("event", "PART_PROCESS_READY");
            resultBody.put("success", true);
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("processId", response.getProcessId());
            payload.put("method", response.getMethod());
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
                        props.setHeader("success", true);
                        return message;
                    }
            );
            log.info("CastingCommandConsumer: результат отправлен в iktk.results, projectId={}", projectId);
        } catch (Exception e) {
            log.error("CastingCommandConsumer: ошибка отправки результата", e);
        }
    }
}