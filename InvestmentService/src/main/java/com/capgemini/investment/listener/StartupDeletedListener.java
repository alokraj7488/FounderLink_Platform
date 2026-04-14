package com.capgemini.investment.listener;

import com.capgemini.investment.config.RabbitMQConfig;
import com.capgemini.investment.event.StartupDeletedEvent;
import com.capgemini.investment.event.StartupRejectedEvent;
import com.capgemini.investment.repository.InvestmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StartupDeletedListener {

    private final InvestmentRepository investmentRepository;

    @RabbitListener(queues = RabbitMQConfig.STARTUP_DELETED_QUEUE)
    @Transactional
    public void handleStartupDeleted(StartupDeletedEvent event) {
        // Purging investment history for the deleted startup to keep data clean
        log.info("Received StartupDeletedEvent for startup id: {}. Purging investment data...", event.getId());
        
        try {
            investmentRepository.deleteByStartupId(event.getId());
            log.info("Successfully purged investment data for startup id: {}", event.getId());
        } catch (Exception e) {
            log.error("Error purging investment data for startup id {}: {}", event.getId(), e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.STARTUP_REJECTED_QUEUE)
    @Transactional
    public void handleStartupRejected(StartupRejectedEvent event) {
        // Purging investments for startups that fail the final vetting process
        log.info("Received StartupRejectedEvent for startup id: {}. Purging pending records...", event.getStartupId());
        try {
            investmentRepository.deleteByStartupId(event.getStartupId());
            log.info("Successfully purged investment data for rejected startup: {}", event.getStartupId());
        } catch (Exception e) {
            log.error("Error purging investment data for rejected id {}: {}", event.getStartupId(), e.getMessage());
        }
    }
}
