package com.capgemini.user.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private final String rabbitmqExchange;
    private final String userRegisteredQueue;
    private final String userRegisteredRoutingKey;

    public RabbitMQConfig(
            @Value("${rabbitmq.exchange}") String rabbitmqExchange,
            @Value("${rabbitmq.queue.user-registered}") String userRegisteredQueue,
            @Value("${rabbitmq.routing-key.user-registered}") String userRegisteredRoutingKey) {
        this.rabbitmqExchange = rabbitmqExchange;
        this.userRegisteredQueue = userRegisteredQueue;
        this.userRegisteredRoutingKey = userRegisteredRoutingKey;
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(rabbitmqExchange);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return QueueBuilder.durable(userRegisteredQueue).build();
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange exchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(exchange).with(userRegisteredRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
