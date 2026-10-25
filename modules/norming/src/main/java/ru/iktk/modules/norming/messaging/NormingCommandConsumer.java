package ru.iktk.modules.norming.messaging;

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
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.norming.client.AssemblyProcessClient;
import ru.iktk.modules.norming.client.dto.AssemblyProcessDto;
import ru.iktk.modules.norming.client.dto.AssemblyStageDto;
import ru.iktk.modules.norming.dto.MaterialNormRequest;
import ru.iktk.modules.norming.dto.MaterialNormResponse;
import ru.iktk.modules.norming.dto.TimeNormRequest;
import ru.iktk.modules.norming.dto.TimeNormResponse;
import ru.iktk.modules.norming.model.TimeNormSource;
import ru.iktk.modules.norming.service.MaterialNormService;
import ru.iktk.modules.norming.service.TechProcessMapper;
import ru.iktk.modules.norming.service.TimeNormService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer команд оркестратора для расчёта норм времени и расхода материалов.
 *
 * <p>Слушает {@code iktk.norming.queue}. По команде:
 * <ol>
 *   <li>Разбирает JSON (projectId, payload.processId).</li>
 *   <li>Если {@code processId} передан — грузит реальный ТП через
 *       {@link AssemblyProcessClient} (assembly-process-service) и маппит в
 *       {@link TechnologyProcess} через {@link TechProcessMapper}.</li>
 *   <li>Если {@code processId} нет или assembly недоступен — fallback на demo-ТП
 *       (одна операция MACHINING) для отладки.</li>
 *   <li>Вызывает {@link TimeNormService#calculate} и {@link MaterialNormService#calculate}.</li>
 *   <li>Публикует результат в {@code iktk.results} с ключом
 *       {@code iktk.result.norming} и событием {@code NORMS_READY}.</li>
 * </ol>
 */
@Component
public class NormingCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(NormingCommandConsumer.class);

    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String RESULT_ROUTING_KEY = "iktk.result.norming";
    private static final String RESULT_EVENT = "NORMS_READY";
    private static final String MODULE_NAME = "norming";

    private final TimeNormService timeNormService;
    private final MaterialNormService materialNormService;
    private final AssemblyProcessClient assemblyProcessClient;
    private final TechProcessMapper techProcessMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public NormingCommandConsumer(TimeNormService timeNormService,
                                  MaterialNormService materialNormService,
                                  AssemblyProcessClient assemblyProcessClient,
                                  TechProcessMapper techProcessMapper,
                                  RabbitTemplate rabbitTemplate,
                                  ObjectMapper objectMapper) {
        this.timeNormService = timeNormService;
        this.materialNormService = materialNormService;
        this.assemblyProcessClient = assemblyProcessClient;
        this.techProcessMapper = techProcessMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${iktk.messaging.commands-queue:iktk.norming.queue}")
    public void onCommand(String bodyJson,
                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
                          @Header(value = AmqpHeaders.CORRELATION_ID, required = false) String correlationId) {

        log.info("NormingCommandConsumer: command received, routingKey={}, correlationId={}",
                routingKey, correlationId);

        String projectId = null;
        try {
            Map<String, Object> body = objectMapper.readValue(bodyJson, new TypeReference<>() {});
            projectId = (String) body.get("projectId");
            Map<String, Object> payload = (Map<String, Object>) body.get("payload");
            String processId = payload != null && payload.get("processId") != null
                    ? String.valueOf(payload.get("processId"))
                    : null;

            log.info("NormingCommandConsumer: processing projectId={}, processId={}",
                    projectId, processId);

            TechnologyProcess process = resolveProcess(projectId, processId);

            TimeNormRequest timeReq = TimeNormRequest.builder()
                    .projectId(projectId)
                    .process(process)
                    .batchSize(BigDecimal.TEN)
                    .source(TimeNormSource.CALCULATED)
                    .enterpriseCode("DEMO-ENTERPRISE")
                    .industryCode("DEMO-INDUSTRY")
                    .build();
            TimeNormResponse timeResp = timeNormService.calculate(timeReq);

            MaterialNormRequest matReq = MaterialNormRequest.builder()
                    .projectId(projectId)
                    .process(process)
                    .batchSize(BigDecimal.TEN)
                    .enterpriseCode("DEMO-ENTERPRISE")
                    .materialCode("STEEL_45")
                    .blankVolumeMm3(BigDecimal.valueOf(100000))
                    .partVolumeMm3(BigDecimal.valueOf(80000))
                    .build();
            MaterialNormResponse matResp = materialNormService.calculate(matReq);

            log.info("NormingCommandConsumer: calculated, timeNormId={}, matNormId={}, totalTshK={}, normPerPart={}",
                    timeResp.getNormId(), matResp.getNormId(),
                    timeResp.getTotalTshK(), matResp.getNormPerPart());

            sendResult(projectId, timeResp, matResp, correlationId, true, null);

        } catch (Exception e) {
            log.error("NormingCommandConsumer: processing error, projectId={}", projectId, e);
            sendResult(projectId, null, null, correlationId, false, e.getMessage());
        }
    }

    /**
     * Получить ТП: реальный из assembly (если processId есть и сервис доступен)
     * или demo (fallback).
     */
    private TechnologyProcess resolveProcess(String projectId, String processId) {
        if (processId == null) {
            log.info("NormingCommandConsumer: processId not provided -> demo TP");
            return buildDemoProcess(projectId, null);
        }
        try {
            AssemblyProcessDto ap = assemblyProcessClient.getProcess(processId);
            List<AssemblyStageDto> stages = assemblyProcessClient.getStages(processId);
            TechnologyProcess tp = techProcessMapper.toTechnologyProcess(ap, stages);
            log.info("NormingCommandConsumer: loaded real TP from assembly, processId={}, stages={}",
                    processId, tp.getStages() != null ? tp.getStages().size() : 0);
            return tp;
        } catch (Exception ex) {
            log.warn("NormingCommandConsumer: assembly unavailable ({}), fallback to demo TP",
                    ex.getMessage());
            return buildDemoProcess(projectId, processId);
        }
    }

    private TechnologyProcess buildDemoProcess(String projectId, String processId) {
        String tpId = (processId != null) ? processId : "demo-process-" + projectId;
        TechnologyProcess process = new TechnologyProcess(
                tpId,
                "DEMO-TP",
                null,
                projectId,
                null,
                null
        );
        process.setName("Демонстрационный ТП (сессия 2, norming)");
        process.setVersion("1.0");
        process.setAuthor("norming-consumer");

        Stage stage = new Stage(
                "demo-stage-1",
                "005",
                "Механическая обработка (демо)",
                Stage.OperationType.MACHINING,
                1
        );
        stage.setTimeNormMin(BigDecimal.valueOf(10));
        stage.setSetupTimeMin(BigDecimal.valueOf(20));

        process.setStages(new ArrayList<>(List.of(stage)));
        return process;
    }

    private void sendResult(String projectId, TimeNormResponse timeResp, MaterialNormResponse matResp,
                            String correlationId, boolean success, String errorMessage) {
        if (projectId == null) {
            log.error("NormingCommandConsumer: projectId missing, cannot send result");
            return;
        }
        try {
            Map<String, Object> resultBody = new HashMap<>();
            resultBody.put("projectId", projectId);
            resultBody.put("event", RESULT_EVENT);
            resultBody.put("success", success);

            Map<String, Object> payload = new HashMap<>();
            if (success && timeResp != null && matResp != null) {
                payload.put("timeNormId", timeResp.getNormId());
                payload.put("materialNormId", matResp.getNormId());
                payload.put("processId", timeResp.getProcessId());
                if (timeResp.getTotalTsh() != null)   payload.put("totalTsh",   timeResp.getTotalTsh().toPlainString());
                if (timeResp.getTotalTshK() != null)  payload.put("totalTshK",  timeResp.getTotalTshK().toPlainString());
                if (timeResp.getTotalTpz() != null)   payload.put("totalTpz",   timeResp.getTotalTpz().toPlainString());
                if (matResp.getNormPerPart() != null) payload.put("normPerPart", matResp.getNormPerPart().toPlainString());
                if (matResp.getMaterialUtilization() != null) payload.put("kim", matResp.getMaterialUtilization().toPlainString());
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
            log.info("NormingCommandConsumer: result sent, projectId={}, success={}", projectId, success);
        } catch (Exception e) {
            log.error("NormingCommandConsumer: result send error, projectId={}", projectId, e);
        }
    }
}
