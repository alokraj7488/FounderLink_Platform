package com.capgemini.team.config;

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

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.team-invite-sent}")
    private String teamInviteSentQueue;

    @Value("${rabbitmq.routing-key.team-invite-sent}")
    private String teamInviteSentRoutingKey;

    public static final String STARTUP_DELETED_QUEUE = "team.startup.deleted.queue";
    public static final String STARTUP_DELETED_ROUTING_KEY = "startup.deleted";
    public static final String STARTUP_REJECTED_QUEUE = "team.startup.rejected.queue";
    public static final String STARTUP_REJECTED_ROUTING_KEY = "startup.rejected";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue teamInviteSentQueue() {
        return new Queue(teamInviteSentQueue, true);
    }

    @Bean
    public Queue startupDeletedQueue() {
        return new Queue(STARTUP_DELETED_QUEUE, true);
    }

    @Bean
    public Queue startupRejectedQueue() {
        return new Queue(STARTUP_REJECTED_QUEUE, true);
    }

    @Bean
    public Binding teamInviteSentBinding(Queue teamInviteSentQueue, TopicExchange exchange) {
        return BindingBuilder.bind(teamInviteSentQueue).to(exchange).with(teamInviteSentRoutingKey);
    }

    @Bean
    public Binding startupDeletedBinding(Queue startupDeletedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(startupDeletedQueue).to(exchange).with(STARTUP_DELETED_ROUTING_KEY);
    }

    @Bean
    public Binding startupRejectedBinding(Queue startupRejectedQueue, TopicExchange exchange) {
        return BindingBuilder.bind(startupRejectedQueue).to(exchange).with(STARTUP_REJECTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
