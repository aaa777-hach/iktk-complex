package ru.iktk.modules.forging.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.forging.dto.ForgingRequest;
import ru.iktk.modules.forging.dto.ForgingResponse;
import ru.iktk.modules.forging.model.ForgingMethod;
import ru.iktk.modules.forging.service.ForgingProcessService;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты ForgingCommandConsumer (@RabbitListener).
 *
 * <p>Проверяем: happy path (designProcess + convertAndSend), invalid JSON
 * (projectId=null -> sendResult early-return), service throws, default objectRef,
 * payload.objectRef в ForgingRequest.
 */
@ExtendWith(MockitoExtension.class)
class ForgingCommandConsumerTest {

    @Mock private ForgingProcessService forgingProcessService;
    @Mock private RabbitTemplate rabbitTemplate;

    private ForgingCommandConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new ForgingCommandConsumer(forgingProcessService, rabbitTemplate, objectMapper);
    }

    private ForgingResponse response() {
        return ForgingResponse.builder()
                .projectId("p1").processId("proc-1").method(ForgingMethod.HOT_DIE_FORGING)
                .transitions(new ArrayList<>()).build();
    }

    @Test
    void onCommand_happyPath_sendsResult() {
        String body = "{\"projectId\":\"p1\",\"state\":\"FORGING_PENDING\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(forgingProcessService.designProcess(any(ForgingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.forging", "corr-1");

        verify(forgingProcessService).designProcess(any(ForgingRequest.class));
        verify(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.forging"),
                anyString(), any(org.springframework.amqp.core.MessagePostProcessor.class));
    }

    @Test
    void onCommand_invalidJson_sendsFailure() {
        // projectId=null -> sendResult early-return без convertAndSend
        consumer.onCommand("not-json", "iktk.command.forging", null);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(),
                anyString(), any(org.springframework.amqp.core.MessagePostProcessor.class));
    }

    @Test
    void onCommand_serviceThrows_sendsFailure() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(forgingProcessService.designProcess(any(ForgingRequest.class)))
                .thenThrow(new OrchestrationException("boom"));

        consumer.onCommand(body, "iktk.command.forging", "corr-1");

        verify(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.forging"),
                anyString(), any(org.springframework.amqp.core.MessagePostProcessor.class));
    }

    @Test
    void onCommand_defaultObjectRef() {
        String body = "{\"projectId\":\"p1\"}";
        when(forgingProcessService.designProcess(any(ForgingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.forging", null);

        ArgumentCaptor<ForgingRequest> captor = ArgumentCaptor.forClass(ForgingRequest.class);
        verify(forgingProcessService).designProcess(captor.capture());
        assertThat(captor.getValue().getObjectRef()).isEqualTo("default-object");
    }

    @Test
    void onCommand_payloadObjectRef_usedInRequest() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-42\"}}";
        when(forgingProcessService.designProcess(any(ForgingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.forging", null);

        ArgumentCaptor<ForgingRequest> captor = ArgumentCaptor.forClass(ForgingRequest.class);
        verify(forgingProcessService).designProcess(captor.capture());
        assertThat(captor.getValue().getObjectRef()).isEqualTo("geo-42");
        assertThat(captor.getValue().getProjectId()).isEqualTo("p1");
    }
}