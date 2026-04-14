package com.capgemini.payment.service;

import com.capgemini.payment.entity.Payment;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@founderlink.com");
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
    }

    // ─── sendPaymentSuccessEmailToInvestor ───────────────────────────────────

    @Test
    void sendPaymentSuccessEmailToInvestor_success_sendsEmail() {
        Payment payment = buildPayment();

        emailService.sendPaymentSuccessEmailToInvestor(payment);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentSuccessEmailToInvestor_mailSenderThrows_doesNotPropagate() {
        Payment payment = buildPayment();
        doThrow(new RuntimeException("SMTP failure")).when(mailSender).send(any(MimeMessage.class));

        // Should NOT throw — exception is caught internally
        emailService.sendPaymentSuccessEmailToInvestor(payment);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentSuccessEmailToInvestor_nullEmail_skipsSending() {
        Payment payment = buildPayment();
        payment.setInvestorEmail(null);

        emailService.sendPaymentSuccessEmailToInvestor(payment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ─── sendPaymentRejectedEmailToInvestor ──────────────────────────────────
    
    @Test
    void sendPaymentRejectedEmailToInvestor_success_sendsEmail() {
        Payment payment = buildPayment();

        emailService.sendPaymentRejectedEmailToInvestor(payment);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentRejectedEmailToInvestor_nullEmail_skipsSending() {
        Payment payment = buildPayment();
        payment.setInvestorEmail("");

        emailService.sendPaymentRejectedEmailToInvestor(payment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ─── sendPaymentReceivedEmailToFounder ───────────────────────────────────

    @Test
    void sendPaymentReceivedEmailToFounder_success_sendsEmail() {
        Payment payment = buildPayment();

        emailService.sendPaymentReceivedEmailToFounder(payment);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentReceivedEmailToFounder_mailSenderThrows_doesNotPropagate() {
        Payment payment = buildPayment();
        doThrow(new RuntimeException("SMTP failure")).when(mailSender).send(any(MimeMessage.class));

        // Should NOT throw — exception is caught internally
        emailService.sendPaymentReceivedEmailToFounder(payment);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaymentReceivedEmailToFounder_nullEmail_skipsSending() {
        Payment payment = buildPayment();
        payment.setFounderEmail(null);

        emailService.sendPaymentReceivedEmailToFounder(payment);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Payment buildPayment() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setInvestorId(10L);
        payment.setFounderId(20L);
        payment.setStartupId(30L);
        payment.setStartupName("StartupX");
        payment.setInvestorName("InvestorY");
        payment.setAmount(5000.0);
        payment.setInvestorEmail("investor@example.com");
        payment.setFounderEmail("founder@example.com");
        payment.setRazorpayPaymentId("pay_xyz");
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        return payment;
    }
}
