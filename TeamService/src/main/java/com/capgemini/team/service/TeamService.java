package com.capgemini.team.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import com.capgemini.team.dto.InvitationRequest;
import com.capgemini.team.dto.InvitationResponse;
import com.capgemini.team.dto.RoleUpdateRequest;
import com.capgemini.team.dto.TeamMemberResponse;
import com.capgemini.team.entity.TeamInvitation;
import com.capgemini.team.entity.TeamMember;
import com.capgemini.team.enums.InvitationStatus;
import com.capgemini.team.enums.TeamRole;
import com.capgemini.team.event.EventPublisher;
import com.capgemini.team.event.TeamInviteSentEvent;
import com.capgemini.team.exception.BadRequestException;
import com.capgemini.team.exception.DuplicateResourceException;
import com.capgemini.team.exception.ResourceNotFoundException;
import com.capgemini.team.exception.ServiceUnavailableException;
import com.capgemini.team.exception.UnauthorizedException;
import com.capgemini.team.feign.StartupClient;
import com.capgemini.team.feign.StartupDTO;
import com.capgemini.team.feign.UserClient;
import com.capgemini.team.feign.UserDTO;
import com.capgemini.team.mapper.TeamMapper;
import com.capgemini.team.repository.TeamInvitationRepository;
import com.capgemini.team.repository.TeamMemberRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TeamService implements TeamCommandService, TeamQueryService {

    private final TeamInvitationRepository invitationRepository;
    private final TeamMemberRepository memberRepository;
    private final StartupClient startupClient;
    private final UserClient userClient;
    private final EventPublisher eventPublisher;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final TeamMapper teamMapper;

    public TeamService(TeamInvitationRepository invitationRepository,
                       TeamMemberRepository memberRepository,
                       StartupClient startupClient,
                       UserClient userClient,
                       EventPublisher eventPublisher,
                       CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                       TeamMapper teamMapper) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.startupClient = startupClient;
        this.userClient = userClient;
        this.eventPublisher = eventPublisher;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.teamMapper = teamMapper;
    }

    private StartupDTO fetchStartup(Long startupId) {
        return circuitBreakerFactory.create("startup-service").run(
                () -> startupClient.getStartupById(startupId),
                throwable -> {
                    log.error("[CIRCUIT BREAKER] startup-service unavailable: {}", throwable.getMessage());
                    throw new ServiceUnavailableException(
                            "Startup service is currently unavailable. Please try again later.");
                }
        );
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "teamInvitations", key = "#request.invitedUserId")
    })
    public InvitationResponse inviteCoFounder(InvitationRequest request, Long founderId) {
        StartupDTO startup = fetchStartup(request.getStartupId());
        if (startup == null) {
            throw new ResourceNotFoundException("Startup not found with id: " + request.getStartupId());
        }

        // Only approved startups can build teams
        if (Boolean.TRUE.equals(startup.getIsRejected())) {
            throw new BadRequestException("This startup has been rejected and cannot build a team.");
        }
        if (!Boolean.TRUE.equals(startup.getIsApproved())) {
            throw new BadRequestException("You cannot invite team members until your startup has been approved by an Admin.");
        }

        if (startup.getFounderId().longValue() != founderId.longValue()) {
            throw new UnauthorizedException("Only the startup founder can invite team members");
        }

        // Guard: founder cannot invite themselves
        if (request.getInvitedUserId().equals(founderId)) {
            throw new BadRequestException("You are already the founder of this startup");
        }

        // Guard: cannot invite someone as a FOUNDER or CO_FOUNDER directly (must use functional roles)
        if (request.getRole() == TeamRole.FOUNDER || request.getRole() == TeamRole.CO_FOUNDER) {
            throw new BadRequestException("You can only assign functional roles: CTO, CPO, MARKETING_HEAD, ENGINEERING_LEAD.");
        }

        // Guard: invited user must not already be a team member
        if (memberRepository.existsByStartupIdAndUserId(request.getStartupId(), request.getInvitedUserId())) {
            throw new DuplicateResourceException("This user is already a member of the team");
        }

        // Guard: no duplicate pending invitation
        invitationRepository.findByStartupIdAndInvitedUserIdAndStatus(
                request.getStartupId(), request.getInvitedUserId(), InvitationStatus.PENDING
        ).ifPresent(existing -> {
            throw new DuplicateResourceException("A pending invitation already exists for this user");
        });

        // Auto-add founder as FOUNDER in team members if not already present
        if (!memberRepository.existsByStartupIdAndUserId(request.getStartupId(), founderId)) {
            memberRepository.save(TeamMember.builder()
                    .startupId(request.getStartupId())
                    .userId(founderId)
                    .role(TeamRole.FOUNDER)
                    .build());
            log.info("Auto-added founder {} to team for startup {}", founderId, request.getStartupId());
        }

        TeamInvitation invitation = TeamInvitation.builder()
                .startupId(request.getStartupId())
                .invitedUserId(request.getInvitedUserId())
                .role(request.getRole())
                .status(InvitationStatus.PENDING)
                .build();

        invitation = invitationRepository.save(invitation);

        eventPublisher.publishTeamInviteSent(TeamInviteSentEvent.builder()
                .invitationId(invitation.getId())
                .startupId(invitation.getStartupId())
                .invitedUserId(invitation.getInvitedUserId())
                .role(invitation.getRole().name())
                .build());

        log.info("Team invitation sent: id={}, startupId={}, invitedUserId={}",
                invitation.getId(), invitation.getStartupId(), invitation.getInvitedUserId());

        return teamMapper.toInvitationResponse(invitation, startup);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "teamInvitations", key = "#userId"),
        @CacheEvict(value = "teamMembers", allEntries = true)
    })
    public TeamMemberResponse acceptInvitation(Long invitationId, Long userId) {
        TeamInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with id: " + invitationId));

        if (!invitation.getInvitedUserId().equals(userId)) {
            throw new UnauthorizedException("This invitation is not for the current user");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation is not in PENDING status");
        }

        // One last check: making sure the startup is still approved before they join the team
        StartupDTO startup = fetchStartup(invitation.getStartupId());
        if (Boolean.TRUE.equals(startup.getIsRejected())) {
            throw new BadRequestException("This invitation is no longer valid because the startup has been rejected.");
        }
        if (!Boolean.TRUE.equals(startup.getIsApproved())) {
            throw new BadRequestException("This invitation is no longer valid because the startup is not approved.");
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        TeamMember member = TeamMember.builder()
                .startupId(invitation.getStartupId())
                .userId(userId)
                .role(invitation.getRole())
                .build();

        member = memberRepository.save(member);

        log.info("Team invitation accepted: invitationId={}, userId={}", invitationId, userId);

        return teamMapper.toMemberResponse(member, startup, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = "teamInvitations", key = "#userId")
    public InvitationResponse rejectInvitation(Long invitationId, Long userId) {
        TeamInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with id: " + invitationId));

        if (!invitation.getInvitedUserId().equals(userId)) {
            throw new UnauthorizedException("This invitation is not for the current user");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Invitation is not in PENDING status");
        }

        invitation.setStatus(InvitationStatus.REJECTED);
        invitationRepository.save(invitation);

        log.info("Team invitation rejected: invitationId={}, userId={}", invitationId, userId);

        return teamMapper.toInvitationResponse(invitation);
    }

    @Override
    @Transactional
    @Cacheable(value = "teamMembers", key = "#startupId")
    public List<TeamMemberResponse> getTeamByStartup(Long startupId) {
        // Ensure the startup founder is always present as a team member
        StartupDTO startup = fetchStartup(startupId);
        
        if (!memberRepository.existsByStartupIdAndRole(startupId, TeamRole.FOUNDER)) {
            if (startup != null && startup.getFounderId() != null) {
                memberRepository.save(TeamMember.builder()
                        .startupId(startupId)
                        .userId(startup.getFounderId())
                        .role(TeamRole.FOUNDER)
                        .build());
                log.info("Auto-added founder {} to team for startup {}", startup.getFounderId(), startupId);
            }
        }

        List<TeamMember> members = memberRepository.findByStartupId(startupId);
        if (members.isEmpty()) return List.of();

        // Batch fetch user details to avoid N+1
        String userIds = members.stream()
                .map(m -> String.valueOf(m.getUserId()))
                .collect(Collectors.joining(","));
        
        List<UserDTO> users = List.of();
        try {
            users = userClient.getProfilesBatch(userIds);
        } catch (Exception e) {
            log.warn("Failed to fetch user profiles for team enrichment: {}", e.getMessage());
        }

        Map<Long, UserDTO> userMap = users.stream()
                .collect(Collectors.toMap(UserDTO::getId, u -> u, (u1, u2) -> u1));

        return members.stream()
                .map(m -> teamMapper.toMemberResponse(m, startup, userMap.get(m.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "teamInvitations", key = "#userId")
    public List<InvitationResponse> getMyInvitations(Long userId) {
        return invitationRepository.findByInvitedUserIdOrderByCreatedAtDesc(userId).stream()
                .map(teamMapper::toInvitationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "teamMembers", allEntries = true)
    public TeamMemberResponse updateMemberRole(Long memberId, RoleUpdateRequest request, Long founderId) {
        TeamMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found with id: " + memberId));

        StartupDTO startup = fetchStartup(member.getStartupId());
        if (startup.getFounderId().longValue() != founderId.longValue()) {
            throw new UnauthorizedException("Only the startup founder can manage team roles");
        }

        // Founders can't demote themselves out of their own startup
        if (member.getUserId().equals(founderId)) {
            throw new BadRequestException("Founders cannot update their own FOUNDER role to a functional role.");
        }

        // Guard: cannot reassign someone to FOUNDER or CO_FOUNDER directly
        if (request.getRole() == TeamRole.FOUNDER || request.getRole() == TeamRole.CO_FOUNDER) {
            throw new BadRequestException("You can only assign functional roles: CTO, CPO, MARKETING_HEAD, ENGINEERING_LEAD.");
        }

        member.setRole(request.getRole());
        member = memberRepository.save(member);

        log.info("Team member role updated: memberId={}, newRole={}", memberId, request.getRole());

        // Enrich the response
        UserDTO memberUser = null;
        try {
            memberUser = userClient.getUserById(member.getUserId());
        } catch (Exception e) {
            log.warn("Failed to fetch user details for member role update enrichment: {}", e.getMessage());
        }

        return teamMapper.toMemberResponse(member, startup, memberUser);
    }

}
