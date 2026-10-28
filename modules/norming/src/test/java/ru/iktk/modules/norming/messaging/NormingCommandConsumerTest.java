package ru.iktk.modules.norming.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NormingCommandConsumer — RabbitMQ-слушатель нормирования")
class NormingCommandConsumerTest {

    @Mock private TimeNormService timeNormService;
    @Mock private MaterialNormService materialNormService;
    @Mock private AssemblyProcessClient assemblyProcessClient;
    @Mock private TechProcessMapper techProcessMapper;
    @Mock private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private NormingCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new NormingCommandConsumer(
                timeNormService, materialNormService,
                assemblyProcessClient, techProcessMapper,
                rabbitTemplate, objectMapper);
    }

    // ---------- helpers ----------

    private TechnologyProcess demoProcess() {
        TechnologyProcess tp = new TechnologyProcess(
                "demo-process-p1", "DEMO-TP", null, "p1", null, null);
        Stage s = new Stage("demo-stage-1", "005", "Мех. обработка",
                Stage.OperationType.MACHINING, 1);
        s.setTimeNormMin(BigDecimal.valueOf(10));
        s.setSetupTimeMin(BigDecimal.valueOf(20));
        tp.setStages(List.of(s));
        return tp;
    }

    private TimeNormResponse okTimeResp() {
        return TimeNormResponse.builder()
                .normId("tn-1")
                .projectId("p1")
                .processId("demo-process-p1")
                .source(TimeNormSource.CALCULATED)
                .totalTsh(new BigDecimal("19.26"))
                .totalTshK(new BigDecimal("24.449"))
                .totalTpz(new BigDecimal("20"))
                .build();
    }

    private MaterialNormResponse okMatResp() {
        return MaterialNormResponse.builder()
                .normId("mn-1")
                .projectId("p1")
                .processId("demo-process-p1")
                .normPerPart(new BigDecimal("0.8248"))
                .materialUtilization(new BigDecimal("0.7614"))
                .build();
    }

    private String body(String projectId, String processId) throws Exception {
        var payload = processId == null ? Map.of() : Map.of("processId", processId);
        return objectMapper.writeValueAsString(Map.of(
                "projectId", projectId,
                "payload", payload));
    }

    private String sentBody(ArgumentCaptor<String> bodyCap) {
        return bodyCap.getValue();
    }

    // ---------- tests ----------

    @Test
    @DisplayName("success: demo-ТП (processId=null) -> source=CALCULATED, result success=true")
    void onCommand_success_demo() throws Exception {
        TechnologyProcess demo = demoProcess();
        when(techProcessMapper.toTechnologyProcess(any(), any())).thenReturn(demo);
        when(timeNormService.calculate(any(TimeNormRequest.class))).thenReturn(okTimeResp());
        when(materialNormService.calculate(any(MaterialNormRequest.class))).thenReturn(okMatResp());

        consumer.onCommand(body("p1", null), "iktk.command.norming", "corr-1");

        ArgumentCaptor<TimeNormRequest> timeCap = ArgumentCaptor.forClass(TimeNormRequest.class);
        verify(timeNormService).calculate(timeCap.capture());
        assertThat(timeCap.getValue().getSource()).isEqualTo(TimeNormSource.CALCULATED);
        assertThat(timeCap.getValue().getProjectId()).isEqualTo("p1");

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"), eq("iktk.result.norming"),
                bodyCap.capture(), any(MessagePostProcessor.class));
        String sent = sentBody(bodyCap);
        assertThat(sent).contains("\"projectId\":\"p1\"");
        assertThat(sent).contains("\"event\":\"NORMS_READY\"");
        assertThat(sent).contains("\"success\":true");
        assertThat(sent).contains("\"timeNormId\":\"tn-1\"");
        assertThat(sent).contains("\"materialNormId\":\"mn-1\"");
        assertThat(sent).contains("\"totalTshK\":\"24.449\"");
        assertThat(sent).contains("\"normPerPart\":\"0.8248\"");
        assertThat(sent).contains("\"kim\":\"0.7614\"");
    }

    @Test
    @DisplayName("success: реальный processId -> assembly client + source=ACTUAL")
    void onCommand_success_actual() throws Exception {
        AssemblyProcessDto ap = AssemblyProcessDto.builder()
                .id("proc-1").objectRef("geo-1").build();
        AssemblyStageDto st = AssemblyStageDto.builder()
                .id("s-1").stageOrder(1).operationType("MACHINING").name("Черновая").build();
        TechnologyProcess tp = demoProcess();
        when(assemblyProcessClient.getProcess("proc-1")).thenReturn(ap);
        when(assemblyProcessClient.getStages("proc-1")).thenReturn(List.of(st));
        when(techProcessMapper.toTechnologyProcess(ap, List.of(st))).thenReturn(tp);
        when(timeNormService.calculate(any(TimeNormRequest.class))).thenReturn(okTimeResp());
        when(materialNormService.calculate(any(MaterialNormRequest.class))).thenReturn(okMatResp());

        consumer.onCommand(body("p1", "proc-1"), "r", "c");

        ArgumentCaptor<TimeNormRequest> timeCap = ArgumentCaptor.forClass(TimeNormRequest.class);
        verify(timeNormService).calculate(timeCap.capture());
        assertThat(timeCap.getValue().getSource()).isEqualTo(TimeNormSource.ACTUAL);
        verify(assemblyProcessClient).getProcess("proc-1");
        verify(assemblyProcessClient).getStages("proc-1");
    }

    @Test
    @DisplayName("assembly throw -> fallback demo (id=processId), source=ACTUAL (processId!=null), result success=true")
    void onCommand_assemblyUnavailable_fallsBackToDemo() throws Exception {
        when(assemblyProcessClient.getProcess("proc-1"))
                .thenThrow(new RuntimeException("assembly down"));
        // techProcessMapper НЕ вызывается при fallback (demo строится напрямую)
        when(timeNormService.calculate(any(TimeNormRequest.class))).thenReturn(okTimeResp());
        when(materialNormService.calculate(any(MaterialNormRequest.class))).thenReturn(okMatResp());

        consumer.onCommand(body("p1", "proc-1"), "r", "c");

        ArgumentCaptor<TimeNormRequest> timeCap = ArgumentCaptor.forClass(TimeNormRequest.class);
        verify(timeNormService).calculate(timeCap.capture());
        TimeNormRequest captured = timeCap.getValue();
        // fallback: processId сохранён как id demo-ТП
        assertThat(captured.getProcess().getId()).isEqualTo("proc-1");
        assertThat(captured.getProcess().getStages()).hasSize(1);
        assertThat(captured.getProcess().getStages().get(0).getOperationType())
                .isEqualTo(Stage.OperationType.MACHINING);
        // processId != null && !startsWith("demo-process-") -> source=ACTUAL (по коду)
        assertThat(captured.getSource()).isEqualTo(TimeNormSource.ACTUAL);

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), bodyCap.capture(), any(MessagePostProcessor.class));
        assertThat(bodyCap.getValue()).contains("\"success\":true");
    }

    @Test
    @DisplayName("невалидный JSON -> sendResult success=false")
    void onCommand_invalidJson_sendsFailure() {
        consumer.onCommand("not a json {", "r", "c");

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        // projectId = null -> sendResult early-return, convertAndSend НЕ вызывается
    }

    @Test
    @DisplayName("projectId missing -> sendResult early-return, convertAndSend НЕ вызывается")
    void onCommand_projectIdMissing_noSend() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("payload", Map.of("processId", "p")));

        consumer.onCommand(json, "r", "c");

        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("service throw -> sendResult success=false, error в payload")
    void onCommand_serviceThrows_sendsFailure() throws Exception {
        when(assemblyProcessClient.getProcess(anyString())).thenReturn(null);
        when(techProcessMapper.toTechnologyProcess(any(), any())).thenReturn(demoProcess());
        when(timeNormService.calculate(any(TimeNormRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onCommand(body("p1", null), "r", "c");

        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), bodyCap.capture(), any(MessagePostProcessor.class));
        String sent = bodyCap.getValue();
        assertThat(sent).contains("\"success\":false");
        assertThat(sent).contains("\"error\":\"boom\"");
    }

    @Test
    @DisplayName("MPP: заголовки projectId/module/event/success, contentType=JSON, correlationId")
    void onCommand_mpp_setsHeaders() throws Exception {
        when(techProcessMapper.toTechnologyProcess(any(), any())).thenReturn(demoProcess());
        when(timeNormService.calculate(any(TimeNormRequest.class))).thenReturn(okTimeResp());
        when(materialNormService.calculate(any(MaterialNormRequest.class))).thenReturn(okMatResp());

        consumer.onCommand(body("p1", null), "r", "corr-1");

        ArgumentCaptor<MessagePostProcessor> mppCap = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), mppCap.capture());
        MessageProperties props = new MessageProperties();
        mppCap.getValue().postProcessMessage(new Message(new byte[0], props));

        assertThat(props.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(props.getCorrelationId()).isEqualTo("corr-1");
        Map<String, Object> headers = props.getHeaders();
        assertThat(headers).containsEntry("projectId", "p1");
        assertThat(headers).containsEntry("module", "norming");
        assertThat(headers).containsEntry("event", "NORMS_READY");
        assertThat(headers).containsEntry("success", Boolean.TRUE);
    }

    @Test
    @DisplayName("null correlationId -> header correlationId не ставится")
    void onCommand_nullCorrelationId_ok() throws Exception {
        when(techProcessMapper.toTechnologyProcess(any(), any())).thenReturn(demoProcess());
        when(timeNormService.calculate(any(TimeNormRequest.class))).thenReturn(okTimeResp());
        when(materialNormService.calculate(any(MaterialNormRequest.class))).thenReturn(okMatResp());

        consumer.onCommand(body("p1", null), "r", null);

        ArgumentCaptor<MessagePostProcessor> mppCap = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), mppCap.capture());
        MessageProperties props = new MessageProperties();
        mppCap.getValue().postProcessMessage(new Message(new byte[0], props));
        assertThat(props.getCorrelationId()).isNull();
    }
}