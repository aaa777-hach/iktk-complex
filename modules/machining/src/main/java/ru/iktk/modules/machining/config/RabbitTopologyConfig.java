package ru.iktk.modules.machining.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Топология RabbitMQ для модуля механообработки (по образцу forging).
 */
@Configuration
public class RabbitTopologyConfig {

    @Value("${iktk.messaging.commands-exchange:iktk.commands}")
    private String commandsExchange;

    @Value("${iktk.messaging.routing-prefix:iktk.command}")
    private String routingPrefix;

    @Value("${iktk.messaging.commands-queue:iktk.machining.queue}")
    private String commandsQueue;

    @Bean
    public TopicExchange commandsExchange() {
        return ExchangeBuilder.topicExchange(commandsExchange).durable(true).build();
    }

    @Bean
    public Queue machiningCommandsQueue() {
        return QueueBuilder.durable(commandsQueue).build();
    }

    @Bean
    public Binding machiningCommandsBinding(Queue machiningCommandsQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(machiningCommandsQueue)
                .to(commandsExchange)
                .with(routingPrefix + ".machining");
    }
}