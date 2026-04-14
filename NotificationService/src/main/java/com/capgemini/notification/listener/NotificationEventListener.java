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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationCommandService notificationService;
    private final EmailService emailService;

    @RabbitListener(queues = "${rabbitmq.queue.user-registered}")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered event â€” userId={} email={} role={}", event.getUserId(), event.getEmail(), event.getRole());

        if (event.getUserId() != null) {
            notificationService.createNotification(
                    event.getUserId(),
                    "Welcome to FounderLink, " + event.getName() + "! Your " + formatRole(event.getRole()) + " profile has been created successfully.",
                    NotificationType.USER_REGISTERED
            );
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.startup-created}")
    public void handleStartupCreated(StartupCreatedEvent event) {
        log.info("Received startup created event for startupId: {}", event.getStartupId());
        notificationService.createNotification(
                event.getFounderId(),
                "Your startup \"" + event.getStartupName() + "\" has been submitted for review.",
                NotificationType.STARTUP_CREATED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.investment-created}")
    public void handleInvestmentCreated(InvestmentCreatedEvent event) {
        log.info("Received investment created event for startupId: {}", event.getStartupId());
        notificationService.createNotification(
                event.getFounderId(),
                "New investment request of amount â‚¹" + formatAmount(event.getAmount()) + " received for your startup.",
                NotificationType.INVESTMENT_CREATED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.investment-approved}")
    public void handleInvestmentApproved(InvestmentApprovedEvent event) {
        log.info("Received investment approved event for investorId: {}", event.getInvestorId());
        notificationService.createNotification(
                event.getInvestorId(),
                "Your investment of amount â‚¹" + formatAmount(event.getAmount()) + " has been approved.",
                NotificationType.INVESTMENT_APPROVED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.startup-rejected}")
    public void handleStartupRejected(StartupRejectedEvent event) {
        log.info("Received startup rejected event for startupId: {}", event.getStartupId());
        notificationService.createNotification(
                event.getFounderId(),
                "We're sorry, your startup \"" + event.getStartupName() + "\" has been reviewed and was not approved at this time. We're not moving forward with this submission.",
                NotificationType.STARTUP_REJECTED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.team-invite-sent}")
    public void handleTeamInvite(TeamInviteSentEvent event) {
        log.info("Received team invite event for userId: {}", event.getInvitedUserId());
        notificationService.createNotification(
                event.getInvitedUserId(),
                "You have been invited to join a startup team as " + event.getRole(),
                NotificationType.TEAM_INVITE_SENT
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment-failed}")
    public void handlePaymentFailed(PaymentSuccessEvent event) {
        log.info("Received payment rejected event â€” investorId={} founderId={} startup={}", event.getInvestorId(), event.getFounderId(), event.getStartupName());
        
        if (event.getInvestorId() != null) {
            notificationService.createNotification(
                    event.getInvestorId(),
                    "Your investment of â‚¹" + formatAmount(event.getAmount()) + " in " + event.getStartupName() + " has failed or was rejected. Please check your payment method.",
                    NotificationType.PAYMENT_REJECTED
            );
        }
        if (event.getFounderId() != null) {
            notificationService.createNotification(
                    event.getFounderId(),
                    "You rejected the investment of â‚¹" + formatAmount(event.getAmount()) + " from " + event.getInvestorName() + " for " + event.getStartupName() + ". A refund has been issued to the investor.",
                    NotificationType.PAYMENT_REJECTED
            );
        }
    }

    @RabbitListener(queues = "${rabbitmq.queue.payment-success}")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        log.info("Received payment success event â€” investorId={} founderId={} startup={}", event.getInvestorId(), event.getFounderId(), event.getStartupName());

        if (event.getInvestorId() != null) {
            notificationService.createNotification(
                    event.getInvestorId(),
                    "Your payment of â‚¹" + formatAmount(event.getAmount()) + " for " + event.getStartupName() + " was accepted by the founder!",
                    NotificationType.PAYMENT_SUCCESS
            );
        }
        if (event.getFounderId() != null) {
            notificationService.createNotification(
                    event.getFounderId(),
                    event.getInvestorName() + " has invested â‚¹" + formatAmount(event.getAmount()) + " in " + event.getStartupName() + ". Investment confirmed.",
                    NotificationType.PAYMENT_SUCCESS
            );
        }
    }

    private String formatAmount(Object amount) {
        if (amount == null) return "0";
        try {
            if (amount instanceof java.math.BigDecimal) {
                return ((java.math.BigDecimal) amount).stripTrailingZeros().toPlainString();
            }
            if (amount instanceof Number) {
                double val = ((Number) amount).doubleValue();
                if (val == (long) val) return String.format("%d", (long) val);
                return String.format("%.2f", val);
            }
            return amount.toString();
        } catch (Exception e) {
            return String.valueOf(amount);
        }
    }

    private String formatRole(String role) {
        if (role == null) return "member";
        return switch (role.toUpperCase()) {
            case "ROLE_FOUNDER"   -> "Founder";
            case "ROLE_INVESTOR"  -> "Investor";
            case "ROLE_COFOUNDER" -> "Co-Founder";
            case "ROLE_ADMIN"     -> "Admin";
            default               -> "member";
        };
    }
}
