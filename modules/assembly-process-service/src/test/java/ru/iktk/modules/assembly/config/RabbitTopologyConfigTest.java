package ru.iktk.modules.assembly.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RabbitTopologyConfig — RabbitMQ топология")
class RabbitTopologyConfigTest {

    private RabbitTopologyConfig cfg;

    @BeforeEach
    void setUp() {
        cfg = new RabbitTopologyConfig();
        ReflectionTestUtils.setField(cfg, "commandsExchange", "iktk.commands");
        ReflectionTestUtils.setField(cfg, "routingPrefix", "iktk.command");
        ReflectionTestUtils.setField(cfg, "commandsQueue", "iktk.assembly-process-service.queue");
    }

    @Test
    @DisplayName("commandsExchange: topic, durable, не autoDelete")
    void commandsExchange_topicDurable() {
        TopicExchange ex = cfg.commandsExchange();
        assertThat(ex.getName()).isEqualTo("iktk.commands");
        assertThat(ex.getType()).isEqualTo(ExchangeTypes.TOPIC);
        assertThat(ex.isDurable()).isTrue();
        assertThat(ex.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("assemblyCommandsQueue: durable, не exclusive, не autoDelete")
    void assemblyCommandsQueue_durable() {
        Queue q = cfg.assemblyCommandsQueue();
        assertThat(q.getName()).isEqualTo("iktk.assembly-process-service.queue");
        assertThat(q.isDurable()).isTrue();
        assertThat(q.isExclusive()).isFalse();
        assertThat(q.isAutoDelete()).isFalse();
    }

    @Test
    @DisplayName("assemblyCommandsBinding: routing key = prefix + '.assembly-process-service'")
    void assemblyCommandsBinding_routingKey() {
        Queue q = cfg.assemblyCommandsQueue();
        TopicExchange ex = cfg.commandsExchange();
        Binding b = cfg.assemblyCommandsBinding(q, ex);
        assertThat(b.getExchange()).isEqualTo("iktk.commands");
        assertThat(b.getDestination()).isEqualTo("iktk.assembly-process-service.queue");
        assertThat(b.getRoutingKey()).isEqualTo("iktk.command.assembly-process-service");
    }
}