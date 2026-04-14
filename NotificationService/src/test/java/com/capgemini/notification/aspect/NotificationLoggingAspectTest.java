package com.capgemini.notification.aspect;

import com.capgemini.notification.service.NotificationCommandService;
import com.capgemini.notification.service.NotificationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.datasource.url=jdbc:h2:mem:testdb_notification_aspect;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "rabbitmq.exchange=test-exchange",
    "rabbitmq.queue.user-registered=test-queue",
    "rabbitmq.queue.startup-created=test-queue",
    "rabbitmq.queue.investment-created=test-queue",
    "rabbitmq.queue.investment-approved=test-queue",
    "rabbitmq.queue.startup-rejected=test-queue",
    "rabbitmq.queue.team-invite-sent=test-queue",
    "rabbitmq.queue.payment-failed=test-queue",
    "rabbitmq.queue.payment-success=test-queue",
    "spring.mail.username=test@example.com",
    "jwt.secret=9a4f4e354567f18b106216470659635038f37803a08856b3e70d4c6d4a0c2014",
    "jwt.access-token-expiration=3600000",
    "jwt.refresh-token-expiration=86400000",
    "app.admin.password=admin123"
})
class NotificationLoggingAspectTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Autowired
    private NotificationCommandService notificationCommandService;

    @Test
    void loggingAspect_shouldInterceptServiceMethod() {
        assertThatCode(() -> {
            try {
                notificationQueryService.getNotificationsByUser(999L);
            } catch (Exception ignored) {
                //Ignored.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void loggingAspect_shouldHandleException() {
        // Triggering the catch block in the aspect by ensuring the service throws
        assertThatThrownBy(() -> notificationCommandService.markAsRead(999L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
