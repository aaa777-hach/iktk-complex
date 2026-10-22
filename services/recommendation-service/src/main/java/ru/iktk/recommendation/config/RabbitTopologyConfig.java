package ru.iktk.recommendation.config;

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
    private String commandsExchangeName;

    @Value("${iktk.messaging.results-exchange:iktk.results}")
    private String resultsExchangeName;

    @Value("${iktk.messaging.routing-prefix:iktk.command}")
    private String routingPrefix;

    @Value("${iktk.messaging.commands-queue:iktk.recommendation-service.queue}")
    private String commandsQueueName;

    @Bean
    public TopicExchange recommendationCommandsExchange() {
        return ExchangeBuilder.topicExchange(commandsExchangeName).durable(true).build();
    }

    @Bean
    public TopicExchange recommendationResultsExchange() {
        return ExchangeBuilder.topicExchange(resultsExchangeName).durable(true).build();
    }

    @Bean
    public Queue recommendationCommandsQueue() {
        return QueueBuilder.durable(commandsQueueName).build();
    }

    @Bean
    public Binding recommendationCommandsBinding(Queue recommendationCommandsQueue,
                                                 TopicExchange recommendationCommandsExchange) {
        return BindingBuilder.bind(recommendationCommandsQueue)
                .to(recommendationCommandsExchange)
                .with(routingPrefix + ".recommendation");
    }
}