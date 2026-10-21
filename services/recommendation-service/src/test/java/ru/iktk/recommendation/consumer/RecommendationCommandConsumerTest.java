package ru.iktk.recommendation.consumer;

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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.service.GapAnalysisService;
import ru.iktk.recommendation.service.OutsourcingService;

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
@DisplayName("RecommendationCommandConsumer — RabbitMQ-слушатель")
class RecommendationCommandConsumerTest {

    @Mock private GapAnalysisService gapAnalysisService;
    @Mock private OutsourcingService outsourcingService;
    @Mock private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private RecommendationCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new RecommendationCommandConsumer(gapAnalysisService, outsourcingService,
                rabbitTemplate, objectMapper);
        ReflectionTestUtils.setField(consumer, "resultsExchange", "iktk.results");
        ReflectionTestUtils.setField(consumer, "resultsRoutingPrefix", "iktk.result");
    }

    private GapAnalysisResponse okAnalysis() {
        return GapAnalysisResponse.builder()
                .id("a1").projectId("p1").status("COMPLETED")
                .gapsCount(3).recommendationsCount(5).strategy("HYBRID")
                .build();
    }

    @Test
    @DisplayName("onCommand: EQUIPMENT_GAP_RESOLUTION -> run + publish RECOMMENDATIONS_READY")
    void onCommand_gapResolution_publishes() throws Exception {
        when(gapAnalysisService.run(any())).thenReturn(okAnalysis());

        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "p1",
                "state", "EQUIPMENT_GAP_RESOLUTION",
                "payload", Map.of("requirements", java.util.List.of())
        ));

        consumer.onCommand(body, "iktk.command.recommendation");

        verify(gapAnalysisService).run(any());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("iktk.results"),
                eq("iktk.result.RECOMMENDATIONS_READY"), captor.capture(), any(org.springframework.amqp.core.MessagePostProcessor.class));
        assertThat(captor.getValue()).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("onCommand: OUTSOURCING_SELECTED -> select + publish OUTSOURCING_SELECTED")
    void onCommand_outsourcingSelected_publishes() throws Exception {
        when(outsourcingService.select(eq("o1"), anyString())).thenReturn(
                OutsourcingOptionDto.builder().id("o1").contractor("A").build());

        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "p1",
                "state", "OUTSOURCING_SELECTED",
                "payload", Map.of("optionId", "o1", "comment", "ok")
        ));

        consumer.onCommand(body, "iktk.command.recommendation");

        verify(outsourcingService).select(eq("o1"), eq("ok"));
        verify(rabbitTemplate).convertAndSend(eq("iktk.results"),
                eq("iktk.result.OUTSOURCING_SELECTED"), any(Object.class), any(org.springframework.amqp.core.MessagePostProcessor.class));
    }

    @Test
    @DisplayName("onCommand: unknown state -> fallback на gap-анализ")
    void onCommand_unknownState_fallbackToGapAnalysis() throws Exception {
        when(gapAnalysisService.run(any())).thenReturn(okAnalysis());

        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "p1",
                "state", "SOMETHING_ELSE",
                "payload", Map.of()
        ));

        consumer.onCommand(body, "iktk.command.recommendation");

        verify(gapAnalysisService).run(any());
    }

    @Test
    @DisplayName("onCommand: service бросает исключение -> не публикует")
    void onCommand_serviceThrows_noPublish() throws Exception {
        when(gapAnalysisService.run(any())).thenThrow(new RuntimeException("boom"));

        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "p1",
                "state", "EQUIPMENT_GAP_RESOLUTION"
        ));

        consumer.onCommand(body, "iktk.command.recommendation");

        verify(rabbitTemplate, never()).convertAndSend(
                anyString(), anyString(), any(Object.class), any(org.springframework.amqp.core.MessagePostProcessor.class));
    }
}