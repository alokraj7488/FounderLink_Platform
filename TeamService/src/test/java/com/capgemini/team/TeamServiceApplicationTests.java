package com.capgemini.team;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.datasource.url=jdbc:h2:mem:testdb_teamservice;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "rabbitmq.exchange=test-exchange",
    "rabbitmq.routing-key.team-invite-sent=test-routing-key",
    "rabbitmq.queue.team-invite-sent=test-queue",
    "spring.mail.username=test@example.com",
    "jwt.secret=9a4f4e354567f18b106216470659635038f37803a08856b3e70d4c6d4a0c2014",
    "jwt.access-token-expiration=3600000",
    "jwt.refresh-token-expiration=86400000",
    "app.admin.password=admin123"
})
class TeamServiceApplicationTests {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;


    @Test
    void contextLoads() {
    }

}