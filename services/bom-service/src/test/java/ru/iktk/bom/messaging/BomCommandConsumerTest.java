package ru.iktk.bom.messaging;

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
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.service.BomService;

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
@DisplayName("BomCommandConsumer — RabbitMQ-слушатель BOM")
class BomCommandConsumerTest {

    @Mock private BomService bomService;
    @Mock private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private BomCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new BomCommandConsumer(bomService, rabbitTemplate, objectMapper);
    }

    private BomResponse okResponse() {
        return BomResponse.builder()
                .projectId("proj1")
                .assemblyId("u1")
                .bomId("u1")
                .status("COMPLETED")
                .positionsCount(3)
                .connectionsCount(1)
                .message("ok")
                .build();
    }

    @Test
    @DisplayName("onCommand: успех -> convertAndSend в iktk.results с BOM_EXTRACTED")
    void onCommand_success() throws Exception {
        when(bomService.analyze(any(BomAnalyzeRequest.class))).thenReturn(okResponse());
        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "proj1",
                "payload", Map.of("geometryId", "geo-1", "cadSystem", "SIEMENS")
        ));

        consumer.onCommand(body, "iktk.command.bom", "corr-1");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"),
                eq("iktk.result.bom-service"),
                bodyCaptor.capture(),
                any(MessagePostProcessor.class));

        String sent = bodyCaptor.getValue();
        assertThat(sent).contains("\"projectId\":\"proj1\"");
        assertThat(sent).contains("\"event\":\"BOM_EXTRACTED\"");
        assertThat(sent).contains("\"success\":true");
        assertThat(sent).contains("\"assemblyId\":\"u1\"");
        assertThat(sent).contains("\"positionsCount\":\"3\"");
    }

    @Test
    @DisplayName("onCommand: ошибка сервиса -> success=false, payload.error")
    void onCommand_serviceError_sendsFailure() throws Exception {
        when(bomService.analyze(any(BomAnalyzeRequest.class)))
                .thenThrow(new RuntimeException("boom"));
        String body = objectMapper.writeValueAsString(Map.of("projectId", "proj1"));

        consumer.onCommand(body, "r", "c");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"), eq("iktk.result.bom-service"),
                bodyCaptor.capture(), any(MessagePostProcessor.class));

        assertThat(bodyCaptor.getValue()).contains("\"success\":false");
        assertThat(bodyCaptor.getValue()).contains("\"error\":\"boom\"");
    }

    @Test
    @DisplayName("onCommand: невалидный JSON -> projectId=null -> result не отправляется")
    void onCommand_invalidJson_noSend() {
        consumer.onCommand("not a json {", "r", "c");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("onCommand: нет projectId -> result не отправляется")
    void onCommand_noProjectId_noSend() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("payload", Map.of("geometryId", "geo-1")));
        consumer.onCommand(json, "r", "c");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("onCommand: payload geometryId/cadSystem передаются в BomAnalyzeRequest")
    void onCommand_payloadFields() throws Exception {
        when(bomService.analyze(any(BomAnalyzeRequest.class))).thenReturn(okResponse());
        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "proj1",
                "payload", Map.of("geometryId", "geo-1", "cadSystem", "SIEMENS")
        ));

        consumer.onCommand(body, "r", null);

        ArgumentCaptor<BomAnalyzeRequest> captor = ArgumentCaptor.forClass(BomAnalyzeRequest.class);
        verify(bomService).analyze(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo("proj1");
        assertThat(captor.getValue().getGeometryId()).isEqualTo("geo-1");
        assertThat(captor.getValue().getCadSystem()).isEqualTo("SIEMENS");
    }

    @Test
    @DisplayName("onCommand: пустой payload -> geometryId=null, cadSystem=null")
    void onCommand_emptyPayload_defaults() throws Exception {
        when(bomService.analyze(any(BomAnalyzeRequest.class))).thenReturn(okResponse());
        String json = objectMapper.writeValueAsString(Map.of("projectId", "proj1"));

        consumer.onCommand(json, "r", null);

        ArgumentCaptor<BomAnalyzeRequest> captor = ArgumentCaptor.forClass(BomAnalyzeRequest.class);
        verify(bomService).analyze(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo("proj1");
        assertThat(captor.getValue().getGeometryId()).isNull();
        assertThat(captor.getValue().getCadSystem()).isNull();
    }

    @Test
    @DisplayName("onCommand: пустой projectId -> result с success=false")
    void onCommand_emptyProjectId() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("projectId", "", "payload", Map.of()));
        when(bomService.analyze(any(BomAnalyzeRequest.class)))
                .thenThrow(new RuntimeException("projectId не задан"));

        consumer.onCommand(json, "r", "c");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("iktk.results"), eq("iktk.result.bom-service"),
                bodyCaptor.capture(), any(MessagePostProcessor.class));
        assertThat(bodyCaptor.getValue()).contains("\"success\":false");
    }
}