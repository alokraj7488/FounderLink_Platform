package com.capgemini.team.listener;

import com.capgemini.team.config.RabbitMQConfig;
import com.capgemini.team.event.StartupDeletedEvent;
import com.capgemini.team.event.StartupRejectedEvent;
import com.capgemini.team.repository.TeamInvitationRepository;
import com.capgemini.team.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StartupDeletedListener {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;

    @RabbitListener(queues = RabbitMQConfig.STARTUP_DELETED_QUEUE)
    @Transactional
    public void handleStartupDeleted(StartupDeletedEvent event) {
        // Purging team members and invites since the startup no longer exists
        log.info("Received StartupDeletedEvent for startup id: {}. Purging team data...", event.getId());
        
        try {
            teamMemberRepository.deleteByStartupId(event.getId());
            teamInvitationRepository.deleteByStartupId(event.getId());
            log.info("Successfully purged team data for startup id: {}", event.getId());
        } catch (Exception e) {
            log.error("Error purging team data for startup id {}: {}", event.getId(), e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMQConfig.STARTUP_REJECTED_QUEUE)
    @Transactional
    public void handleStartupRejected(StartupRejectedEvent event) {
        // Cleaning up pending invites for startups that were not approved
        log.info("Received StartupRejectedEvent for startup id: {}. Purging pending records...", event.getStartupId());
        try {
            teamMemberRepository.deleteByStartupId(event.getStartupId());
            teamInvitationRepository.deleteByStartupId(event.getStartupId());
            log.info("Successfully purged team records for rejected startup: {}", event.getStartupId());
        } catch (Exception e) {
            log.error("Error purging team data for rejected id {}: {}", event.getStartupId(), e.getMessage());
        }
    }
}
