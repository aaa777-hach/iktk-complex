package ru.iktk.recommendation.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RabbitTopologyConfig — очереди, обменники, биндинги")
class RabbitTopologyConfigTest {

    private RabbitTopologyConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitTopologyConfig();
        ReflectionTestUtils.setField(config, "commandsExchangeName", "iktk.commands");
        ReflectionTestUtils.setField(config, "resultsExchangeName", "iktk.results");
        ReflectionTestUtils.setField(config, "routingPrefix", "iktk.command");
        ReflectionTestUtils.setField(config, "commandsQueueName", "iktk.recommendation-service.queue");
    }

    @Test
    @DisplayName("Queue -> durable с именем iktk.recommendation-service.queue")
    void queue_isDurable() {
        Queue q = config.recommendationCommandsQueue();
        assertThat(q.getName()).isEqualTo("iktk.recommendation-service.queue");
        assertThat(q.isDurable()).isTrue();
    }

    @Test
    @DisplayName("TopicExchange commands/results -> durable с правильными именами")
    void exchanges_haveNames() {
        TopicExchange cmd = config.recommendationCommandsExchange();
        TopicExchange res = config.recommendationResultsExchange();
        assertThat(cmd.getName()).isEqualTo("iktk.commands");
        assertThat(cmd.isDurable()).isTrue();
        assertThat(res.getName()).isEqualTo("iktk.results");
        assertThat(res.isDurable()).isTrue();
    }

    @Test
    @DisplayName("Binding -> routing key iktk.command.recommendation")
    void binding_hasRoutingKey() {
        Queue q = config.recommendationCommandsQueue();
        TopicExchange ex = config.recommendationCommandsExchange();
        Binding b = config.recommendationCommandsBinding(q, ex);
        assertThat(b.getRoutingKey()).isEqualTo("iktk.command.recommendation");
        assertThat(b.getDestination()).isEqualTo("iktk.recommendation-service.queue");
    }

    @Test
    @DisplayName("MessageConverter -> Jackson2JsonMessageConverter")
    void messageConverter_isJackson() {
        assertThat(config.jacksonMessageConverter())
                .isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}