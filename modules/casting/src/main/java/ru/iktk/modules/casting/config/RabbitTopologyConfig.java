package ru.iktk.modules.casting.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Value("${iktk.messaging.commands-exchange:iktk.commands}")
    private String commandsExchange;

    @Value("${iktk.messaging.routing-prefix:iktk.command}")
    private String routingPrefix;

    @Value("${iktk.messaging.commands-queue:iktk.casting.queue}")
    private String commandsQueue;

    @Bean
    public TopicExchange commandsExchange() {
        return ExchangeBuilder.topicExchange(commandsExchange).durable(true).build();
    }

    @Bean
    public Queue castingCommandsQueue() {
        return QueueBuilder.durable(commandsQueue).build();
    }

    @Bean
    public Binding castingCommandsBinding(Queue castingCommandsQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(castingCommandsQueue)
                .to(commandsExchange)
                .with(routingPrefix + ".casting");
    }
}