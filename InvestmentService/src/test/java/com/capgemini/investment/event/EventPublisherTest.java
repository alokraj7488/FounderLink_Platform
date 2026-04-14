package com.capgemini.investment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

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
        ReflectionTestUtils.setField(eventPublisher, "investmentCreatedRoutingKey", "investment.created");
        ReflectionTestUtils.setField(eventPublisher, "investmentApprovedRoutingKey", "investment.approved");
    }

    @Test
    void publishInvestmentCreated_shouldSendToCorrectExchangeAndRoutingKey() {
        InvestmentCreatedEvent event = InvestmentCreatedEvent.builder()
                .investmentId(1L).startupId(100L).investorId(200L)
                .founderId(300L).amount(BigDecimal.valueOf(50000))
                .build();

        eventPublisher.publishInvestmentCreated(event);

        verify(rabbitTemplate).convertAndSend("founderlink.exchange", "investment.created", event);
    }

    @Test
    void publishInvestmentApproved_shouldSendToCorrectExchangeAndRoutingKey() {
        InvestmentApprovedEvent event = InvestmentApprovedEvent.builder()
                .investmentId(1L).startupId(100L).investorId(200L)
                .amount(BigDecimal.valueOf(50000))
                .build();

        eventPublisher.publishInvestmentApproved(event);

        verify(rabbitTemplate).convertAndSend("founderlink.exchange", "investment.approved", event);
    }
}
