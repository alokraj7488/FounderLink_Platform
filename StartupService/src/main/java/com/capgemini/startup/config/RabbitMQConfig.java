package com.capgemini.startup.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private final String rabbitmqExchange;
    private final String startupCreatedQueue;
    private final String startupCreatedRoutingKey;
    private final String startupRejectedQueue;
    private final String startupRejectedRoutingKey;

    public RabbitMQConfig(
            @Value("${rabbitmq.exchange}") String rabbitmqExchange,
            @Value("${rabbitmq.queue.startup-created}") String startupCreatedQueue,
            @Value("${rabbitmq.routing-key.startup-created}") String startupCreatedRoutingKey,
            @Value("${rabbitmq.queue.startup-rejected}") String startupRejectedQueue,
            @Value("${rabbitmq.routing-key.startup-rejected}") String startupRejectedRoutingKey) {
        this.rabbitmqExchange = rabbitmqExchange;
        this.startupCreatedQueue = startupCreatedQueue;
        this.startupCreatedRoutingKey = startupCreatedRoutingKey;
        this.startupRejectedQueue = startupRejectedQueue;
        this.startupRejectedRoutingKey = startupRejectedRoutingKey;
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(rabbitmqExchange);
    }

    @Bean
    public Queue startupCreatedQueue() {
        return QueueBuilder.durable(startupCreatedQueue).build();
    }

    @Bean
    public Queue startupRejectedQueue() {
        return QueueBuilder.durable(startupRejectedQueue).build();
    }

    @Bean
    public Binding startupCreatedBinding(Queue startupCreatedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(startupCreatedQueue).to(exchange).with(startupCreatedRoutingKey);
    }

    @Bean
    public Binding startupRejectedBinding(Queue startupRejectedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(startupRejectedQueue).to(exchange).with(startupRejectedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}

