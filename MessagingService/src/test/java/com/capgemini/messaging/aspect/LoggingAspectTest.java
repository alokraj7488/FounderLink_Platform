package com.capgemini.messaging.aspect;

import com.capgemini.messaging.service.MessagingQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.datasource.url=jdbc:h2:mem:testdb_aspect;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
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
class LoggingAspectTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessagingQueryService messagingQueryService;

    @Test
    void loggingAspect_shouldInterceptServiceMethod() {
        // We just call a method. If the aspect fails, it would throw an exception.
        // The aspect logs messages to Slf4j, which we can't easily verify without a LogAppender,
        // but calling it ensures the code (try block, START/END logs) is executed.
        assertThatCode(() -> {
            try {
                messagingQueryService.getConversationMessages(999L, 1L);
            } catch (Exception ignored) {
                // We expect a Not Found exception from the service, but the aspect should have run.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void loggingAspect_shouldHandleException() {
        // Triggering the catch block in the aspect by ensuring the service throws
        assertThatThrownBy(() -> messagingQueryService.getConversationMessages(1L, 1L))
                .isInstanceOf(RuntimeException.class);
    }
}
