package com.capgemini.authservice.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WelcomeEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private WelcomeEmailService welcomeEmailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(welcomeEmailService, "fromEmail", "test@founderlink.com");
    }

    @Test
    void sendWelcome_withFounderRole_shouldSendEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        welcomeEmailService.sendWelcome("alice@example.com", "Alice", "ROLE_FOUNDER");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcome_withInvestorRole_shouldSendEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        welcomeEmailService.sendWelcome("bob@example.com", "Bob", "ROLE_INVESTOR");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcome_withCoFounderRole_shouldSendEmail() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        welcomeEmailService.sendWelcome("charlie@example.com", "Charlie", "ROLE_COFOUNDER");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcome_withUnknownRole_shouldSendEmailWithDefaultTemplate() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        welcomeEmailService.sendWelcome("test@example.com", "Test", "UNKNOWN");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcome_whenMailSenderFails_shouldNotThrowException() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP error"));

        // Should not throw — the method catches exceptions internally
        welcomeEmailService.sendWelcome("fail@example.com", "Fail", "ROLE_FOUNDER");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
