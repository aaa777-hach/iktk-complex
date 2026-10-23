package ru.iktk.recommendation.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.repository.OutsourcingOptionRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("IT: RecommendationCommandConsumer на реальных RabbitMQ + PostgreSQL")
class RecommendationConsumerIntegrationTest extends AbstractIntegrationTest {

    private static final String COMMANDS_QUEUE = "iktk.recommendation-service.queue";
    private static final String RESULTS_EXCHANGE = "iktk.results";
    private static final String TEST_RESULTS_QUEUE = "it.results.queue";

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private OutsourcingOptionRepository outsourcingRepository;
    @Autowired private ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository gapRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EquipmentClient equipmentClient;

    @BeforeEach
    void setUp() {
        Queue q = new Queue(TEST_RESULTS_QUEUE, false, false, true);
        rabbitAdmin.declareQueue(q);
        rabbitAdmin.declareBinding(BindingBuilder.bind(q)
                .to(new TopicExchange(RESULTS_EXCHANGE, true, false))
                .with("iktk.result.OUTSOURCING_SELECTED"));
        rabbitAdmin.purgeQueue(TEST_RESULTS_QUEUE, false);
    }

    @Test
    @DisplayName("OUTSOURCING_SELECTED: consumer обновляет БД и публикует событие в iktk.results")
    void outsourcingSelected_updatesDbAndPublishes() throws Exception {
        ru.iktk.recommendation.model.EquipmentGapAnalysis gap =
                new ru.iktk.recommendation.model.EquipmentGapAnalysis(
                        "it-gap-1", "it-project-1", "HYBRID");
        gapRepository.save(gap);

        OutsourcingOption option = OutsourcingOption.builder()
                .id("it-opt-1").gapId("it-gap-1").projectId("it-project-1")
                .type("CONTRACTOR").operation("TURNING").contractor("IT Contractor")
                .cost(new BigDecimal("1000")).qualityRating(new BigDecimal("0.9"))
                .status("PENDING")
                .build();
        outsourcingRepository.save(option);

        String body = objectMapper.writeValueAsString(Map.of(
                "projectId", "it-project-1",
                "state", "OUTSOURCING_SELECTED",
                "payload", Map.of("optionId", "it-opt-1", "comment", "it-test")
        ));
        rabbitTemplate.convertAndSend(COMMANDS_QUEUE, body);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            OutsourcingOption updated = outsourcingRepository.findById("it-opt-1").orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("SELECTED");
            assertThat(updated.getComment()).isEqualTo("it-test");
        });

        Object received = rabbitTemplate.receiveAndConvert(TEST_RESULTS_QUEUE, 5000);
        assertThat(received).isNotNull();
        String json = String.valueOf(received);
        assertThat(json).contains("OUTSOURCING_SELECTED");
        assertThat(json).contains("it-opt-1");
    }
}