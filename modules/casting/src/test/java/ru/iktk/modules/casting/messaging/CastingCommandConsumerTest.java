package ru.iktk.modules.casting.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.casting.dto.CastingRequest;
import ru.iktk.modules.casting.dto.CastingResponse;
import ru.iktk.modules.casting.model.CastingMethod;
import ru.iktk.modules.casting.service.CastingProcessService;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Тесты CastingCommandConsumer (@RabbitListener).
 *
 * <p>4-й аргумент convertAndSend — MessagePostProcessor (Spring AMQP),
 * НЕ java.util.function.Consumer (грабля forging).
 */
@ExtendWith(MockitoExtension.class)
class CastingCommandConsumerTest {

    @Mock private CastingProcessService castingProcessService;
    @Mock private RabbitTemplate rabbitTemplate;

    private CastingCommandConsumer consumer;
    private ObjectMapper objectMapper;

     @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new CastingCommandConsumer(castingProcessService, rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(consumer, "resultExchange", "iktk.results");
        ReflectionTestUtils.setField(consumer, "resultRoutingKey", "iktk.result.casting");
        ReflectionTestUtils.setField(consumer, "resultSendMaxAttempts", 3);
        ReflectionTestUtils.setField(consumer, "resultSendBackoffMs", 0L);  // без sleep в тестах
        ReflectionTestUtils.setField(consumer, "demoFallback", true);
    }

    private CastingResponse response() {
        return CastingResponse.builder()
                .projectId("p1").processId("proc-1").method(CastingMethod.SAND_CASTING)
                .stages(new ArrayList<>()).build();
    }

    @Test
    void onCommand_happyPath_sendsResult() {
        String body = "{\"projectId\":\"p1\",\"state\":\"CASTING_PENDING\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(castingProcessService.designProcess(any(CastingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.casting", "corr-1");

        verify(castingProcessService).designProcess(any(CastingRequest.class));
        verify(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.casting"),
                anyString(), any(MessagePostProcessor.class));
    }

    @Test
    void onCommand_invalidJson_sendsFailure() {
        // projectId=null -> sendResult early-return без convertAndSend
        consumer.onCommand("not-json", "iktk.command.casting", null);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(),
                anyString(), any(MessagePostProcessor.class));
    }

    @Test
    void onCommand_serviceThrows_sendsFailure() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(castingProcessService.designProcess(any(CastingRequest.class)))
                .thenThrow(new OrchestrationException("boom"));

        consumer.onCommand(body, "iktk.command.casting", "corr-1");

        verify(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.casting"),
                anyString(), any(MessagePostProcessor.class));
    }

    @Test
    void onCommand_defaultObjectRef() {
        String body = "{\"projectId\":\"p1\"}";
        when(castingProcessService.designProcess(any(CastingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.casting", null);

        ArgumentCaptor<CastingRequest> captor = ArgumentCaptor.forClass(CastingRequest.class);
        verify(castingProcessService).designProcess(captor.capture());
        assertThat(captor.getValue().getObjectRef()).isEqualTo("default-object");
    }

    @Test
    void onCommand_payloadObjectRef_usedInRequest() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-42\"}}";
        when(castingProcessService.designProcess(any(CastingRequest.class))).thenReturn(response());

        consumer.onCommand(body, "iktk.command.casting", null);

        ArgumentCaptor<CastingRequest> captor = ArgumentCaptor.forClass(CastingRequest.class);
        verify(castingProcessService).designProcess(captor.capture());
        assertThat(captor.getValue().getObjectRef()).isEqualTo("geo-42");
        assertThat(captor.getValue().getProjectId()).isEqualTo("p1");
    }
        @Test
    void onCommand_sendFailsOnce_retrySucceeds() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(castingProcessService.designProcess(any(CastingRequest.class))).thenReturn(response());
        org.mockito.Mockito.doThrow(new RuntimeException("amqp down"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.casting"),
                        anyString(), any(MessagePostProcessor.class));

        consumer.onCommand(body, "iktk.command.casting", "corr-1");

        verify(rabbitTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(eq("iktk.results"), eq("iktk.result.casting"),
                        anyString(), any(MessagePostProcessor.class));
    }

    @Test
    void onCommand_sendFailsAllAttempts_throwsOrchestrationException() {
        String body = "{\"projectId\":\"p1\",\"payload\":{\"objectRef\":\"geo-1\"}}";
        when(castingProcessService.designProcess(any(CastingRequest.class))).thenReturn(response());
        org.mockito.Mockito.doThrow(new RuntimeException("amqp down"))
                .when(rabbitTemplate).convertAndSend(eq("iktk.results"), eq("iktk.result.casting"),
                        anyString(), any(MessagePostProcessor.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> consumer.onCommand(body, "iktk.command.casting", "corr-1"))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("3");
    }
}