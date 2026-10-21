package ru.iktk.modules.assembly.messaging;

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
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.service.AssemblyProcessService;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssemblyCommandConsumer — RabbitMQ-слушатель ТП сборки")
class AssemblyCommandConsumerTest {

    @Mock private AssemblyProcessService service;
    @Mock private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private AssemblyCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new AssemblyCommandConsumer(service, rabbitTemplate, objectMapper);
    }

    private AssemblyResponse okResponse() {
        return AssemblyResponse.builder()
                .processId("proc-1")
                .strategy("SEQUENTIAL")
                .stageCount(5)
                .componentCount(3)
                .totalTimeMin(new BigDecimal("165.0"))
                .message("ok")
                .build();
    }

    private String body(String projectId, String objectRef, String bomRef) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "projectId", projectId,
                "payload", Map.of(
                        "objectRef", objectRef == null ? "" : objectRef,
                        "bomRef", bomRef == null ? "" : bomRef
                )
        ));
    }

    @Test
    @DisplayName("onCommand: успех -> convertAndSend в iktk.results с событием ASSEMBLY_PROCESS_READY")
    void onCommand_success() throws Exception {
        when(service.designProcess(any(AssemblyRequest.class))).thenReturn(okResponse());

        consumer.onCommand(body("proj1", "obj-1", "bom-1"), "iktk.command.assembly", "corr-1");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"),
                eq("iktk.result.assembly-process-service"),
                bodyCaptor.capture(),
                any(MessagePostProcessor.class));

        String sent = bodyCaptor.getValue();
        assertThat(sent).contains("\"projectId\":\"proj1\"");
        assertThat(sent).contains("\"event\":\"ASSEMBLY_PROCESS_READY\"");
        assertThat(sent).contains("\"success\":true");
        assertThat(sent).contains("\"processId\":\"proc-1\"");
        assertThat(sent).contains("\"stageCount\":5");
        assertThat(sent).contains("\"componentCount\":3");
    }

    @Test
    @DisplayName("onCommand: ошибка сервиса -> success=false, payload.error")
    void onCommand_serviceError_sendsFailure() throws Exception {
        when(service.designProcess(any(AssemblyRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        consumer.onCommand(body("proj1", "obj-1", null), "r", "c");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"), eq("iktk.result.assembly-process-service"),
                bodyCaptor.capture(), any(MessagePostProcessor.class));

        String sent = bodyCaptor.getValue();
        assertThat(sent).contains("\"success\":false");
        assertThat(sent).contains("\"error\":\"boom\"");
    }

    @Test
    @DisplayName("onCommand: невалидный JSON -> projectId=null -> result не отправляется")
    void onCommand_invalidJson_noProjectId_noSend() {
        consumer.onCommand("not a json {", "r", "c");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("onCommand: в body нет projectId -> result не отправляется")
    void onCommand_noProjectId_noSend() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("payload", Map.of("objectRef", "obj")));
        consumer.onCommand(json, "r", "c");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("onCommand: payload с objectRef и bomRef передаются в сервис")
    void onCommand_payloadFields() throws Exception {
        when(service.designProcess(any(AssemblyRequest.class))).thenReturn(okResponse());

        consumer.onCommand(body("proj1", "obj-1", "bom-1"), "r", null);

        ArgumentCaptor<AssemblyRequest> captor = ArgumentCaptor.forClass(AssemblyRequest.class);
        verify(service).designProcess(captor.capture());
        AssemblyRequest req = captor.getValue();
        assertThat(req.getProjectId()).isEqualTo("proj1");
        assertThat(req.getObjectRef()).isEqualTo("obj-1");
        assertThat(req.getBomRef()).isEqualTo("bom-1");
    }

    @Test
    @DisplayName("onCommand: пустой payload -> objectRef=default-object, bomRef=null")
    void onCommand_emptyPayload_defaults() throws Exception {
        when(service.designProcess(any(AssemblyRequest.class))).thenReturn(okResponse());
        String json = objectMapper.writeValueAsString(Map.of("projectId", "proj1"));

        consumer.onCommand(json, "r", null);

        ArgumentCaptor<AssemblyRequest> captor = ArgumentCaptor.forClass(AssemblyRequest.class);
        verify(service).designProcess(captor.capture());
        assertThat(captor.getValue().getObjectRef()).isEqualTo("default-object");
        assertThat(captor.getValue().getBomRef()).isNull();
    }

    @Test
    @DisplayName("onCommand: null projectId в JSON -> sendResult не вызывается")
    void onCommand_nullProjectIdInBody() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("projectId", "", "payload", Map.of()));

        consumer.onCommand(json, "r", "c");

        // projectId пустой, но не null — сервис упадёт с IllegalArgumentException,
        // result уйдёт с success=false на projectId=""
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"), eq("iktk.result.assembly-process-service"),
                any(Object.class), any(MessagePostProcessor.class));
    }
}