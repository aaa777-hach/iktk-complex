package ru.iktk.modules.assembly.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Value("${iktk.messaging.commands-exchange:iktk.commands}")
    private String commandsExchange;

    @Value("${iktk.messaging.routing-prefix:iktk.command}")
    private String routingPrefix;

    @Value("${iktk.messaging.commands-queue:iktk.assembly-process-service.queue}")
    private String commandsQueue;

    @Bean
    public TopicExchange commandsExchange() {
        return ExchangeBuilder.topicExchange(commandsExchange).durable(true).build();
    }

    @Bean
    public Queue assemblyCommandsQueue() {
        return QueueBuilder.durable(commandsQueue).build();
    }

    @Bean
    public Binding assemblyCommandsBinding(Queue assemblyCommandsQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(assemblyCommandsQueue)
                .to(commandsExchange)
                .with(routingPrefix + ".assembly-process-service");
    }
}
