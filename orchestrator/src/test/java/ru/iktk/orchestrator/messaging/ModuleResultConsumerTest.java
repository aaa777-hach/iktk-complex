package ru.iktk.orchestrator.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleResultConsumer — приём результатов от модулей")
class ModuleResultConsumerTest {

    @Mock private OrchestrationService orchestrationService;
    @Mock private DecisionLogService decisionLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ModuleResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ModuleResultConsumer(orchestrationService, decisionLogService, objectMapper);
    }

    private Message message(String body, Map<String, Object> headers) {
        MessageProperties props = new MessageProperties();
        headers.forEach(props::setHeader);
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    @DisplayName("Валидное сообщение: onModuleResult вызван с распакованным payload")
    void onResult_valid_callsOrchestrationService() {
        String body = "{\"projectId\":\"p1\",\"event\":\"NORMS_READY\",\"success\":true,"
                + "\"payload\":{\"result\":\"ok\",\"nested\":{\"a\":1}}}";
        Message msg = message(body, Map.of());

        consumer.onResult(msg, "iktk.result.norming", "corr-1");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(orchestrationService).onModuleResult(
                eq("p1"), eq(ProjectEvent.NORMS_READY), captor.capture(), eq(true));

        Map<String, String> p = captor.getValue();
        assertThat(p).containsEntry("result", "ok");
        assertThat(p).containsEntry("routingKey", "iktk.result.norming");
        assertThat(p).containsEntry("correlationId", "corr-1");
        assertThat(p.get("nested")).contains("\"a\":1");
    }

    @Test
    @DisplayName("Нет projectId -> OrchestrationException + запись в лог с UNKNOWN")
    void onResult_missingProjectId_throws() {
        Message msg = message("{\"event\":\"NORMS_READY\"}", Map.of());

        assertThatThrownBy(() -> consumer.onResult(msg, "r", null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");

        verify(decisionLogService).log(
                eq("UNKNOWN"), eq("MODULE_RESULT_REJECTED"), any(Map.class));
        verifyNoInteractions(orchestrationService);
    }

    @Test
    @DisplayName("Нет event -> OrchestrationException")
    void onResult_missingEvent_throws() {
        Message msg = message("{\"projectId\":\"p1\"}", Map.of());

        assertThatThrownBy(() -> consumer.onResult(msg, "r", null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("event");

        verifyNoInteractions(orchestrationService);
    }

    @Test
    @DisplayName("success из заголовка перекрывает JSON")
    void onResult_successFromHeader_used() {
        String body = "{\"projectId\":\"p1\",\"event\":\"NORMS_READY\"}";
        Message msg = message(body, Map.of("success", "false"));

        consumer.onResult(msg, "r", null);

        verify(orchestrationService).onModuleResult(
                eq("p1"), eq(ProjectEvent.NORMS_READY), any(), eq(false));
    }

    @Test
    @DisplayName("success по умолчанию true, если не указан")
    void onResult_successDefaultsToTrue() {
        String body = "{\"projectId\":\"p1\",\"event\":\"NORMS_READY\"}";
        Message msg = message(body, Map.of());

        consumer.onResult(msg, "r", null);

        verify(orchestrationService).onModuleResult(
                eq("p1"), eq(ProjectEvent.NORMS_READY), any(), eq(true));
    }

    @Test
    @DisplayName("onDeadLetter пишет в журнал с префиксом DLQ")
    void onDeadLetter_logs() {
        String body = "{\"projectId\":\"p1\"}";
        Message msg = message(body, Map.of(
                "projectId", "p1", "module", "norming", "event", "NORMS_READY"));

        consumer.onDeadLetter(msg, "iktk.orchestrator.results.dlq");

        verify(decisionLogService).log(
                eq("p1"), eq("MODULE_RESULT_DLQ"), any(Map.class), eq("SYSTEM"));
    }

    @Test
    @DisplayName("onDeadLetter без projectId -> UNKNOWN")
    void onDeadLetter_noProjectId_usesUnknown() {
        Message msg = message("{}", Map.of());

        consumer.onDeadLetter(msg, "dlq");

        verify(decisionLogService).log(
                eq("UNKNOWN"), eq("MODULE_RESULT_DLQ"), any(Map.class), eq("SYSTEM"));
    }

    // ====== PATCHED_MESSAGING_EXTRA ======

    @Test
    @DisplayName("payload-массив: значения сериализуются в JSON-строку")
    void onResult_payloadWithArray_serialized() {
        String body = "{\"projectId\":\"p1\",\"event\":\"NORMS_READY\","
                + "\"payload\":{\"items\":[{\"id\":1},{\"id\":2}]}}";
        consumer.onResult(message(body, Map.of()), "r", null);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(orchestrationService).onModuleResult(eq("p1"), eq(ProjectEvent.NORMS_READY),
                captor.capture(), eq(true));
        assertThat(captor.getValue().get("items")).contains("[", "{\"id\":1}");
    }

    @Test
    @DisplayName("длинное тело сообщения обрезается до 500 символов + …")
    void onResult_longBody_truncatedInLog() {
        String longBody = "{\"event\":\"NORMS_READY\",\"x\":\"" + "a".repeat(1000) + "\"}";

        assertThatThrownBy(() -> consumer.onResult(message(longBody, Map.of()), "r", null))
                .isInstanceOf(OrchestrationException.class);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(decisionLogService).log(eq("UNKNOWN"), eq("MODULE_RESULT_REJECTED"), captor.capture());
        String loggedBody = captor.getValue().get("body");
        assertThat(loggedBody.length()).isLessThanOrEqualTo(501);
        assertThat(loggedBody).endsWith("…");
    }

    @Test
    @DisplayName("невалидный JSON -> OrchestrationException с routingKey в сообщении")
    void onResult_invalidJson_throws() {
        Message msg = message("not a json {", Map.of());

        assertThatThrownBy(() -> consumer.onResult(msg, "iktk.result.bad", "c-1"))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("iktk.result.bad");
    }

    @Test
    @DisplayName("success=false в JSON (без заголовка) -> onModuleResult(...,false)")
    void onResult_successFromJson_used() {
        String body = "{\"projectId\":\"p1\",\"event\":\"NORMS_READY\",\"success\":false}";
        consumer.onResult(message(body, Map.of()), "r", null);

        verify(orchestrationService).onModuleResult(eq("p1"), eq(ProjectEvent.NORMS_READY),
                any(), eq(false));
    }

    @Test
    @DisplayName("onDeadLetter: длинное тело обрезается, projectId из заголовка")
    void onDeadLetter_longBody_truncated() {
        String body = "{\"big\":\"" + "x".repeat(1000) + "\"}";
        Message msg = message(body, Map.of(
                "projectId", "p-9", "module", "casting", "event", "CASTING_DONE"));

        consumer.onDeadLetter(msg, "iktk.orchestrator.results.dlq");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(decisionLogService).log(eq("p-9"), eq("MODULE_RESULT_DLQ"), captor.capture(), eq("SYSTEM"));
        String loggedBody = captor.getValue().get("body");
        assertThat(loggedBody.length()).isLessThanOrEqualTo(501);
    }
}