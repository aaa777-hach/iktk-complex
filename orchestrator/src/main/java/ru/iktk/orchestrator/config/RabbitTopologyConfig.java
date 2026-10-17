package ru.iktk.orchestrator.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Value("${iktk.messaging.results-exchange:iktk.results}")
    private String resultsExchange;

    @Value("${iktk.messaging.results-queue:iktk.orchestrator.results.queue}")
    private String resultsQueue;

    @Value("${iktk.messaging.dlq:iktk.orchestrator.results.dlq}")
    private String dlq;

    @Bean
    public TopicExchange resultsExchange() {
        return ExchangeBuilder.topicExchange(resultsExchange).durable(true).build();
    }

    @Bean
    public Queue orchestratorResultsQueue() {
        return QueueBuilder.durable(resultsQueue).build();
    }

    @Bean
    public Queue orchestratorResultsDlq() {
        return QueueBuilder.durable(dlq).build();
    }

    @Bean
    public Binding orchestratorResultsBinding(Queue orchestratorResultsQueue, TopicExchange resultsExchange) {
        return BindingBuilder.bind(orchestratorResultsQueue)
                .to(resultsExchange)
                .with("iktk.result.*");
    }

    @Bean
    public Binding orchestratorResultsDlqBinding(Queue orchestratorResultsDlq, TopicExchange resultsExchange) {
        return BindingBuilder.bind(orchestratorResultsDlq)
                .to(resultsExchange)
                .with("iktk.result.dlq");
    }
}