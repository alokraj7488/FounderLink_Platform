package com.capgemini.investment.listener;

import com.capgemini.investment.event.StartupDeletedEvent;
import com.capgemini.investment.event.StartupRejectedEvent;
import com.capgemini.investment.repository.InvestmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartupDeletedListenerTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @InjectMocks
    private StartupDeletedListener startupDeletedListener;

    @Test
    void handleStartupDeleted_shouldCompleteCase() {
        // given
        StartupDeletedEvent event = new StartupDeletedEvent(10L, "Tech", 1L);
        
        // when
        startupDeletedListener.handleStartupDeleted(event);
        
        // then
        verify(investmentRepository).deleteByStartupId(10L);
    }

    @Test
    void handleStartupDeleted_whenException_shouldCatch() {
        // given
        StartupDeletedEvent event = new StartupDeletedEvent(10L, "Tech", 1L);
        doThrow(new RuntimeException("DB Error")).when(investmentRepository).deleteByStartupId(10L);
        
        // when
        startupDeletedListener.handleStartupDeleted(event);
        
        // then
        verify(investmentRepository).deleteByStartupId(10L);
        // Exception should be caught internally
    }

    @Test
    void handleStartupRejected_shouldCompleteCase() {
        // given
        StartupRejectedEvent event = new StartupRejectedEvent(10L, 1L, "Tech");
        
        // when
        startupDeletedListener.handleStartupRejected(event);
        
        // then
        verify(investmentRepository).deleteByStartupId(10L);
    }

    @Test
    void handleStartupRejected_whenException_shouldCatch() {
        // given
        StartupRejectedEvent event = new StartupRejectedEvent(10L, 1L, "Tech");
        doThrow(new RuntimeException("DB Error")).when(investmentRepository).deleteByStartupId(10L);
        
        // when
        startupDeletedListener.handleStartupRejected(event);
        
        // then
        verify(investmentRepository).deleteByStartupId(10L);
        // Exception should be caught internally
    }
}
