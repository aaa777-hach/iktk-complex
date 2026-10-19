package ru.iktk.orchestrator.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleCommandProducer — отправка команд в RabbitMQ")
class ModuleCommandProducerTest {

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private DecisionLogService decisionLogService;

    private ModuleCommandProducer producer;

    @BeforeEach
    void setUp() {
        producer = new ModuleCommandProducer(rabbitTemplate, objectMapper, decisionLogService);
        ReflectionTestUtils.setField(producer, "commandsExchange", "iktk.commands");
        ReflectionTestUtils.setField(producer, "routingPrefix", "iktk.command");
    }

    // ====== send ======

    @Test
    @DisplayName("send: успех — convertAndSend вызван с правильным exchange/routingKey, лог COMMAND_SENT")
    void send_success() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"x\":1}");

        producer.send("p1", "bom-service", ProjectState.NORMING, Map.of("k", "v"));

        verify(rabbitTemplate).convertAndSend(
                eq("iktk.commands"), eq("iktk.command.bom-service"), eq("{\"x\":1}"),
                any(MessagePostProcessor.class));
        verify(decisionLogService).log(eq("p1"), eq("COMMAND_SENT"), any(Map.class));
    }

    @Test
    @DisplayName("send: null projectId -> OrchestrationException, брокер не трогаем")
    void send_nullProjectId_throws() {
        assertThatThrownBy(() -> producer.send(null, "m", ProjectState.NORMING, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("send: пустой module -> OrchestrationException")
    void send_blankModule_throws() {
        assertThatThrownBy(() -> producer.send("p1", " ", ProjectState.NORMING, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("module");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("send: null state -> OrchestrationException")
    void send_nullState_throws() {
        assertThatThrownBy(() -> producer.send("p1", "m", null, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("state");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("send: JsonProcessingException -> OrchestrationException")
    void send_jsonError_throws() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});
        assertThatThrownBy(() -> producer.send("p1", "m", ProjectState.NORMING, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("сериализации");
        verify(decisionLogService, never()).log(eq("p1"), eq("COMMAND_SENT"), any());
    }

    @Test
    @DisplayName("send: ошибка брокера -> OrchestrationException")
    void send_brokerError_throws() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(),
                        any(MessagePostProcessor.class));

        assertThatThrownBy(() -> producer.send("p1", "m", ProjectState.NORMING, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("отправки");
    }

    // ====== retry ======

    @Test
    @DisplayName("retry: attempt из payload (число) + 1, лог COMMAND_RETRY")
    void retry_success_attemptFromNumber() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.retry("p1", ProjectEvent.NORMS_READY, Map.of("attempt", 2));

        verify(rabbitTemplate).convertAndSend(
                eq("iktk.commands"), eq("iktk.command.norming"), eq("{}"),
                any(MessagePostProcessor.class));
        verify(decisionLogService).log(eq("p1"), eq("COMMAND_RETRY"), any(Map.class));
    }

    @Test
    @DisplayName("retry: attempt из payload как String")
    void retry_attemptFromString() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.retry("p1", ProjectEvent.NORMS_READY, Map.of("attempt", "5"));

        verify(rabbitTemplate).convertAndSend(
                eq("iktk.commands"), eq("iktk.command.norming"), eq("{}"),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("retry: невалидный attempt -> 0+1")
    void retry_invalidAttempt_treatedAsZero() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.retry("p1", ProjectEvent.NORMS_READY, Map.of("attempt", "abc"));

        verify(rabbitTemplate).convertAndSend(
                eq("iktk.commands"), eq("iktk.command.norming"), eq("{}"),
                any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("retry: null projectId -> OrchestrationException")
    void retry_nullProjectId_throws() {
        assertThatThrownBy(() -> producer.retry(null, ProjectEvent.NORMS_READY, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("retry: null event -> OrchestrationException")
    void retry_nullEvent_throws() {
        assertThatThrownBy(() -> producer.retry("p1", null, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("event");
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("retry: JsonProcessingException -> OrchestrationException")
    void retry_jsonError_throws() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});
        assertThatThrownBy(() -> producer.retry("p1", ProjectEvent.NORMS_READY, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("retry");
    }

    @Test
    @DisplayName("retry: ошибка брокера -> OrchestrationException")
    void retry_brokerError_throws() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(),
                        any(MessagePostProcessor.class));
        assertThatThrownBy(() -> producer.retry("p1", ProjectEvent.NORMS_READY, Map.of()))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("retry");
    }

    // ====== moduleFor (через retry) ======

    @Test
    @DisplayName("retry: маппинг событий на модули — проверка нескольких пар")
    void retry_moduleMapping() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        producer.retry("p1", ProjectEvent.START_GEOMETRY_INPUT, Map.of());
        producer.retry("p1", ProjectEvent.IS_PART, Map.of());
        producer.retry("p1", ProjectEvent.BOM_EXTRACTED, Map.of());
        producer.retry("p1", ProjectEvent.EQUIPMENT_MATCHED, Map.of());
        producer.retry("p1", ProjectEvent.DOCUMENTATION_READY, Map.of());

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, org.mockito.Mockito.times(5))
                .convertAndSend(eq("iktk.commands"), keys.capture(), eq("{}"),
                        any(MessagePostProcessor.class));
        assertThat(keys.getAllValues()).containsExactly(
                "iktk.command.geometry-analysis",
                "iktk.command.part-process-service",
                "iktk.command.bom-service",
                "iktk.command.equipment-service",
                "iktk.command.documentation-service");
    }

    // ====== геттеры ======

    @Test
    @DisplayName("getCommandsExchange / getRoutingPrefix возвращают значения из @Value")
    void getters_returnConfiguredValues() {
        assertThat(producer.getCommandsExchange()).isEqualTo("iktk.commands");
        assertThat(producer.getRoutingPrefix()).isEqualTo("iktk.command");
    }
}