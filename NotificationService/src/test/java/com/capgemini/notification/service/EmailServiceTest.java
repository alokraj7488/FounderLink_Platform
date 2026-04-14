package com.capgemini.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@example.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendWelcomeEmail_forFounder_shouldSendEmail() {
        emailService.sendWelcomeEmail("founder@example.com", "Alice", "ROLE_FOUNDER");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcomeEmail_forInvestor_shouldSendEmail() {
        emailService.sendWelcomeEmail("investor@example.com", "Bob", "ROLE_INVESTOR");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcomeEmail_forCoFounder_shouldSendEmail() {
        emailService.sendWelcomeEmail("cofounder@example.com", "Charlie", "ROLE_COFOUNDER");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcomeEmail_forDefaultRole_shouldSendEmail() {
        emailService.sendWelcomeEmail("member@example.com", "Dave", "ROLE_MEMBER");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcomeEmail_whenExceptionOccurs_shouldHandleGracefully() {
        // Force an exception during send
        doThrow(new RuntimeException("SMTP Server Down")).when(mailSender).send(any(MimeMessage.class));

        // Should not throw exception to the caller because of try-catch in EmailService
        emailService.sendWelcomeEmail("user@example.com", "Alice", "ROLE_FOUNDER");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
