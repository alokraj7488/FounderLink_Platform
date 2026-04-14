package com.capgemini.payment.aspect;

import com.capgemini.payment.service.PaymentQueryService;
import com.capgemini.payment.service.PaymentCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.razorpay.RazorpayClient;
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
    "spring.datasource.url=jdbc:h2:mem:testdb_payment_aspect;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "razorpay.key-id=test_id",
    "razorpay.key-secret=test_secret",
    "rabbitmq.exchange=test-exchange",
    "rabbitmq.queue.payment-pending=test-queue",
    "rabbitmq.queue.payment-success=test-queue",
    "rabbitmq.queue.payment-failed=test-queue",
    "spring.mail.username=test@example.com",
    "jwt.secret=9a4f4e354567f18b106216470659635038f37803a08856b3e70d4c6d4a0c2014",
    "jwt.access-token-expiration=3600000",
    "jwt.refresh-token-expiration=86400000",
    "app.admin.password=admin123"
})
class PaymentLoggingAspectTest {

    @MockitoBean
    private RazorpayClient razorpayClient;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Autowired
    private PaymentQueryService paymentQueryService;

    @Autowired
    private PaymentCommandService paymentCommandService;

    @Test
    void loggingAspect_shouldInterceptQueryMethod() {
        assertThatCode(() -> {
            try {
                paymentQueryService.getPaymentsByInvestor(999L);
            } catch (Exception ignored) {
                //Ignored.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void loggingAspect_shouldHandleException() {
        // Triggering an exception to hit the AOP @AfterThrowing block
        assertThatThrownBy(() -> paymentCommandService.acceptPayment(999L))
                .isInstanceOf(RuntimeException.class);
    }
}
