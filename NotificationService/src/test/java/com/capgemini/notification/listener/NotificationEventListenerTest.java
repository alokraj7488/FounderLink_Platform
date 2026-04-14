package com.capgemini.notification.listener;

import com.capgemini.notification.enums.NotificationType;
import com.capgemini.notification.event.InvestmentApprovedEvent;
import com.capgemini.notification.event.InvestmentCreatedEvent;
import com.capgemini.notification.event.StartupCreatedEvent;
import com.capgemini.notification.event.StartupRejectedEvent;
import com.capgemini.notification.event.TeamInviteSentEvent;
import com.capgemini.notification.event.PaymentSuccessEvent;
import com.capgemini.notification.event.UserRegisteredEvent;
import com.capgemini.notification.service.EmailService;
import com.capgemini.notification.service.NotificationCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationCommandService notificationCommandService;
    @Mock private EmailService emailService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void handleUserRegistered_shouldCreateNotification() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(10L).name("Alice").email("alice@example.com").role("ROLE_FOUNDER").build();

        listener.handleUserRegistered(event);

        verify(notificationCommandService).createNotification(
                eq(10L),
                contains("Welcome to FounderLink"),
                eq(NotificationType.USER_REGISTERED)
        );
    }

    @Test
    void handleUserRegistered_withNullUserId_shouldNotCreateNotification() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(null).name("Alice").build();

        listener.handleUserRegistered(event);

        verify(notificationCommandService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleStartupCreated_shouldNotifyFounder() {
        StartupCreatedEvent event = StartupCreatedEvent.builder()
                .startupId(100L).founderId(10L).build();

        listener.handleStartupCreated(event);

        verify(notificationCommandService).createNotification(
                eq(10L), contains("submitted for review"), eq(NotificationType.STARTUP_CREATED)
        );
    }

    @Test
    void handleInvestmentCreated_shouldNotifyFounder() {
        // Test with BigDecimal amount
        InvestmentCreatedEvent event = InvestmentCreatedEvent.builder()
                .startupId(100L).founderId(10L).amount(BigDecimal.valueOf(50000)).build();

        listener.handleInvestmentCreated(event);

        verify(notificationCommandService).createNotification(
                eq(10L), contains("50000"), eq(NotificationType.INVESTMENT_CREATED)
        );
    }

    @Test
    void handleInvestmentApproved_shouldNotifyInvestor() {
        // Test with BigDecimal amount
        InvestmentApprovedEvent event = InvestmentApprovedEvent.builder()
                .investorId(20L).amount(BigDecimal.valueOf(50000.55)).build();

        listener.handleInvestmentApproved(event);

        verify(notificationCommandService).createNotification(
                eq(20L), contains("50000.55"), eq(NotificationType.INVESTMENT_APPROVED)
        );
    }

    @Test
    void handleStartupRejected_shouldNotifyFounder() {
        StartupRejectedEvent event = StartupRejectedEvent.builder()
                .startupId(100L).founderId(10L).startupName("TechCo").build();

        listener.handleStartupRejected(event);

        verify(notificationCommandService).createNotification(
                eq(10L), contains("TechCo"), eq(NotificationType.STARTUP_REJECTED)
        );
    }

    @Test
    void handleTeamInvite_shouldNotifyInvitedUser() {
        TeamInviteSentEvent event = TeamInviteSentEvent.builder()
                .invitedUserId(30L).role("COFOUNDER").build();

        listener.handleTeamInvite(event);

        verify(notificationCommandService).createNotification(
                eq(30L), contains("invited"), eq(NotificationType.TEAM_INVITE_SENT)
        );
    }

    @Test
    void handlePaymentSuccess_shouldNotifyBothParties() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .investorId(20L).founderId(10L).startupName("TechCo").investorName("Bob").amount(50000.0).build();

        listener.handlePaymentSuccess(event);

        verify(notificationCommandService).createNotification(
                eq(20L), contains("accepted"), eq(NotificationType.PAYMENT_SUCCESS)
        );
        verify(notificationCommandService).createNotification(
                eq(10L), contains("50000"), eq(NotificationType.PAYMENT_SUCCESS)
        );
    }

    @Test
    void handlePaymentFailed_shouldNotifyBothParties() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .investorId(20L).founderId(10L).startupName("TechCo").investorName("Bob").amount(null).build();

        listener.handlePaymentFailed(event);

        // Verification using contains("0") to avoid encoding issues with the rupee symbol
        verify(notificationCommandService).createNotification(
                eq(20L), contains("0"), eq(NotificationType.PAYMENT_REJECTED)
        );
        verify(notificationCommandService).createNotification(
                eq(10L), contains("rejected"), eq(NotificationType.PAYMENT_REJECTED)
        );
    }

    @Test
    void handlePaymentSuccess_withNullParticipants_shouldNotNotify() {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .investorId(null).founderId(null).startupName("TechCo").amount(50000.0).build();

        listener.handlePaymentSuccess(event);

        verify(notificationCommandService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleUserRegistered_withAllRoles_shouldWork() {
        String[] roles = {"ROLE_FOUNDER", "ROLE_INVESTOR", "ROLE_COFOUNDER", "ROLE_ADMIN", "ROLE_MEMBER", null};
        for (String role : roles) {
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .userId(1L).name("User").role(role).build();
            listener.handleUserRegistered(event);
        }
        // Verify 6 calls occurred (5 roles + null)
        verify(notificationCommandService, times(6)).createNotification(any(), any(), any());
    }
}
