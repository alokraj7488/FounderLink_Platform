package com.capgemini.team.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventPublisher, "exchange", "founderlink.exchange");
        ReflectionTestUtils.setField(eventPublisher, "teamInviteSentRoutingKey", "team.invite.sent");
    }

    @Test
    void publishTeamInviteSent_shouldSendToCorrectExchangeAndRoutingKey() {
        TeamInviteSentEvent event = TeamInviteSentEvent.builder()
                .invitationId(1L).startupId(100L)
                .invitedUserId(200L).role("COFOUNDER")
                .build();

        eventPublisher.publishTeamInviteSent(event);

        verify(rabbitTemplate).convertAndSend("founderlink.exchange", "team.invite.sent", event);
    }
}
