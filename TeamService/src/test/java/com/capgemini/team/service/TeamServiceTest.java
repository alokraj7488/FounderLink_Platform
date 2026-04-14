package com.capgemini.team.service;

import com.capgemini.team.dto.InvitationRequest;
import com.capgemini.team.dto.InvitationResponse;
import com.capgemini.team.dto.RoleUpdateRequest;
import com.capgemini.team.dto.TeamMemberResponse;
import com.capgemini.team.entity.TeamInvitation;
import com.capgemini.team.entity.TeamMember;
import com.capgemini.team.enums.InvitationStatus;
import com.capgemini.team.enums.TeamRole;
import com.capgemini.team.event.EventPublisher;
import com.capgemini.team.exception.BadRequestException;
import com.capgemini.team.exception.DuplicateResourceException;
import com.capgemini.team.exception.ResourceNotFoundException;
import com.capgemini.team.exception.UnauthorizedException;
import com.capgemini.team.feign.StartupClient;
import com.capgemini.team.feign.StartupDTO;
import com.capgemini.team.feign.UserClient;
import com.capgemini.team.feign.UserDTO;
import com.capgemini.team.mapper.TeamMapper;
import com.capgemini.team.repository.TeamInvitationRepository;
import com.capgemini.team.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamServiceTest {

    @Mock
    private TeamInvitationRepository invitationRepository;

    @Mock
    private TeamMemberRepository memberRepository;

    @Mock
    private StartupClient startupClient;

    @Mock
    private UserClient userClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Mock
    private CircuitBreaker startupCircuitBreaker;
    @Mock
    private CircuitBreaker userCircuitBreaker;

    @Mock
    private TeamMapper teamMapper;

    @InjectMocks
    private TeamService teamService;

    private StartupDTO sampleStartup;
    private UserDTO sampleUser;
    private TeamInvitation sampleInvitation;
    private TeamMember sampleMember;

    @BeforeEach
    void setUp() {
        when(circuitBreakerFactory.create("startup-service")).thenReturn(startupCircuitBreaker);
        when(circuitBreakerFactory.create("user-service")).thenReturn(userCircuitBreaker);

        when(startupCircuitBreaker.run(any(), any())).thenAnswer(invocation -> {
            Supplier<?> s = invocation.getArgument(0);
            return s.get();
        });
        when(userCircuitBreaker.run(any(), any())).thenAnswer(invocation -> {
            Supplier<?> s = invocation.getArgument(0);
            return s.get();
        });

        sampleStartup = StartupDTO.builder()
                .id(10L)
                .name("TechStartup")
                .founderId(1L)
                .isApproved(true)
                .isRejected(false)
                .build();
        
        when(startupClient.getStartupById(anyLong())).thenReturn(sampleStartup);

        sampleUser = UserDTO.builder()
                .id(2L)
                .name("Jane Doe")
                .email("jane@example.com")
                .build();

        sampleInvitation = TeamInvitation.builder()
                .id(50L)
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .status(InvitationStatus.PENDING)
                .build();
        sampleInvitation.setCreatedAt(LocalDateTime.now());

        sampleMember = TeamMember.builder()
                .id(200L)
                .startupId(10L)
                .userId(2L)
                .role(TeamRole.CTO)
                .build();
        sampleMember.setJoinedAt(LocalDateTime.now());

        // Default mapper stubs (lenient — only used when the mapper is called)
        when(teamMapper.toInvitationResponse(any(TeamInvitation.class), any())).thenAnswer(inv -> {
            TeamInvitation inv1 = inv.getArgument(0);
            StartupDTO s = inv.getArgument(1);
            InvitationResponse response = InvitationResponse.builder()
                    .id(inv1.getId()).startupId(inv1.getStartupId())
                    .invitedUserId(inv1.getInvitedUserId())
                    .role(inv1.getRole()).status(inv1.getStatus())
                    .createdAt(inv1.getCreatedAt()).build();
            if (s != null) {
                response.setStartupName(s.getName());
            }
            return response;
        });

        // Add back single argument mock
        when(teamMapper.toInvitationResponse(any(TeamInvitation.class))).thenAnswer(inv -> {
            TeamInvitation inv1 = inv.getArgument(0);
            return InvitationResponse.builder()
                    .id(inv1.getId()).startupId(inv1.getStartupId())
                    .invitedUserId(inv1.getInvitedUserId())
                    .role(inv1.getRole()).status(inv1.getStatus())
                    .createdAt(inv1.getCreatedAt()).build();
        });

        when(teamMapper.toMemberResponse(any(TeamMember.class), any(), any())).thenAnswer(inv -> {
            TeamMember m = inv.getArgument(0);
            StartupDTO s = inv.getArgument(1);
            UserDTO u = inv.getArgument(2);
            TeamMemberResponse response = TeamMemberResponse.builder()
                    .id(m.getId()).startupId(m.getStartupId())
                    .userId(m.getUserId()).role(m.getRole())
                    .joinedAt(m.getJoinedAt()).build();
            if (s != null) {
                response.setStartupName(s.getName());
            }
            if (u != null) {
                response.setUserName(u.getName());
            }
            return response;
        });

        // Add back single argument mock
        when(teamMapper.toMemberResponse(any(TeamMember.class))).thenAnswer(inv -> {
            TeamMember m = inv.getArgument(0);
            return TeamMemberResponse.builder()
                    .id(m.getId()).startupId(m.getStartupId())
                    .userId(m.getUserId()).role(m.getRole())
                    .joinedAt(m.getJoinedAt()).build();
        });
    }

    // -------------------------------------------------------------------------
    // inviteCoFounder
    // -------------------------------------------------------------------------

    @Test
    void inviteCoFounder_whenNotFounder_shouldThrowUnauthorizedException() {
        // given
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .build();
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup); // founderId = 1L

        // when / then — pass a different founderId
        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 99L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("founder");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteCoFounder_whenDuplicatePendingInvitation_shouldThrowDuplicateResourceException() {
        // given
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .build();
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup);
        when(userClient.getUserById(2L)).thenReturn(sampleUser);
        when(invitationRepository.findByStartupIdAndInvitedUserIdAndStatus(10L, 2L, InvitationStatus.PENDING))
                .thenReturn(Optional.of(sampleInvitation));

        // when / then
        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("pending invitation");

        verify(invitationRepository, never()).save(any());
    }

    @Test
    void inviteCoFounder_whenValid_shouldSaveAndReturnInvitationResponse() {
        // given
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .build();
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup);
        when(memberRepository.existsByStartupIdAndUserId(10L, 2L)).thenReturn(false);
        when(memberRepository.existsByStartupIdAndUserId(10L, 1L)).thenReturn(true); // founder exists
        when(invitationRepository.findByStartupIdAndInvitedUserIdAndStatus(10L, 2L, InvitationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(invitationRepository.save(any(TeamInvitation.class))).thenReturn(sampleInvitation);
        doNothing().when(eventPublisher).publishTeamInviteSent(any());

        // when
        InvitationResponse response = teamService.inviteCoFounder(request, 1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStartupId()).isEqualTo(10L);
        verify(invitationRepository).save(any(TeamInvitation.class));
    }

    @Test
    void inviteCoFounder_whenStartupNotFound_shouldThrowResourceNotFoundException() {
        InvitationRequest request = InvitationRequest.builder().startupId(999L).build();
        when(startupClient.getStartupById(999L)).thenReturn(null);

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void inviteCoFounder_whenStartupRejected_shouldThrowBadRequestException() {
        sampleStartup.setIsRejected(true);
        InvitationRequest request = InvitationRequest.builder().startupId(10L).build();

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void inviteCoFounder_whenStartupNotApproved_shouldThrowBadRequestException() {
        sampleStartup.setIsApproved(false);
        InvitationRequest request = InvitationRequest.builder().startupId(10L).build();

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approved");
    }

    @Test
    void inviteCoFounder_whenSelfInvite_shouldThrowBadRequestException() {
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(1L) // Founder ID
                .build();

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already the founder");
    }

    @Test
    void inviteCoFounder_whenForbiddenRole_shouldThrowBadRequestException() {
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.FOUNDER) // Forbidden role for invitation
                .build();

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only assign functional roles");
    }

    @Test
    void inviteCoFounder_whenAlreadyMember_shouldThrowDuplicateResourceException() {
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .build();
        when(memberRepository.existsByStartupIdAndUserId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> teamService.inviteCoFounder(request, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void inviteCoFounder_whenFounderNotMember_shouldAutoAddFounder() {
        InvitationRequest request = InvitationRequest.builder()
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .build();
        when(memberRepository.existsByStartupIdAndUserId(10L, 2L)).thenReturn(false);
        when(memberRepository.existsByStartupIdAndUserId(10L, 1L)).thenReturn(false); // Founder missing
        when(invitationRepository.save(any())).thenReturn(sampleInvitation);

        teamService.inviteCoFounder(request, 1L);

        verify(memberRepository).save(argThat(m -> m.getUserId().equals(1L) && m.getRole() == TeamRole.FOUNDER));
    }

    // -------------------------------------------------------------------------
    // acceptInvitation
    // -------------------------------------------------------------------------

    @Test
    void acceptInvitation_whenNotForThisUser_shouldThrowUnauthorizedException() {
        // given
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(sampleInvitation)); // invitedUserId = 2L

        // when / then — pass a different userId
        assertThatThrownBy(() -> teamService.acceptInvitation(50L, 99L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("invitation");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_whenNotPending_shouldThrowBadRequestException() {
        // given
        TeamInvitation acceptedInvitation = TeamInvitation.builder()
                .id(50L)
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .status(InvitationStatus.ACCEPTED)
                .build();
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(acceptedInvitation));

        // when / then
        assertThatThrownBy(() -> teamService.acceptInvitation(50L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void acceptInvitation_whenValid_shouldCreateTeamMember() {
        // given
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(sampleInvitation));
        when(invitationRepository.save(any(TeamInvitation.class))).thenReturn(sampleInvitation);
        when(memberRepository.save(any(TeamMember.class))).thenReturn(sampleMember);

        // when
        TeamMemberResponse response = teamService.acceptInvitation(50L, 2L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getStartupId()).isEqualTo(10L);
        assertThat(response.getRole()).isEqualTo(TeamRole.CTO);
        verify(invitationRepository).save(any(TeamInvitation.class));
        verify(memberRepository).save(any(TeamMember.class));
    }

    @Test
    void acceptInvitation_whenStartupRejected_shouldThrowBadRequestException() {
        sampleStartup.setIsRejected(true);
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(sampleInvitation));

        assertThatThrownBy(() -> teamService.acceptInvitation(50L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void acceptInvitation_whenStartupNotApproved_shouldThrowBadRequestException() {
        sampleStartup.setIsApproved(false);
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(sampleInvitation));

        assertThatThrownBy(() -> teamService.acceptInvitation(50L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not approved");
    }

    // -------------------------------------------------------------------------
    // rejectInvitation
    // -------------------------------------------------------------------------

    @Test
    void rejectInvitation_whenValid_shouldSetRejectedStatus() {
        // given
        when(invitationRepository.findById(50L)).thenReturn(Optional.of(sampleInvitation));
        TeamInvitation rejectedInvitation = TeamInvitation.builder()
                .id(50L)
                .startupId(10L)
                .invitedUserId(2L)
                .role(TeamRole.CTO)
                .status(InvitationStatus.REJECTED)
                .build();
        when(invitationRepository.save(any(TeamInvitation.class))).thenReturn(rejectedInvitation);

        // when
        InvitationResponse response = teamService.rejectInvitation(50L, 2L);

        // then
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.REJECTED);
        verify(invitationRepository).save(any(TeamInvitation.class));
    }

    // -------------------------------------------------------------------------
    // updateMemberRole
    // -------------------------------------------------------------------------

    @Test
    void updateMemberRole_whenNotFounder_shouldThrowUnauthorizedException() {
        // given
        when(memberRepository.findById(200L)).thenReturn(Optional.of(sampleMember)); // startupId = 10L
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup); // founderId = 1L
        RoleUpdateRequest request = RoleUpdateRequest.builder().role(TeamRole.CTO).build();

        // when / then — pass a different founderId
        assertThatThrownBy(() -> teamService.updateMemberRole(200L, request, 99L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("founder");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void updateMemberRole_whenValid_shouldUpdateRole() {
        // given
        when(memberRepository.findById(200L)).thenReturn(Optional.of(sampleMember));
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup); // founderId = 1L
        TeamMember updatedMember = TeamMember.builder()
                .id(200L)
                .startupId(10L)
                .userId(2L)
                .role(TeamRole.CTO)
                .joinedAt(sampleMember.getJoinedAt())
                .build();
        when(memberRepository.save(any(TeamMember.class))).thenReturn(updatedMember);
        when(userClient.getUserById(2L)).thenReturn(sampleUser);
        RoleUpdateRequest request = RoleUpdateRequest.builder().role(TeamRole.CTO).build();

        // when
        TeamMemberResponse response = teamService.updateMemberRole(200L, request, 1L);

        // then
        assertThat(response.getRole()).isEqualTo(TeamRole.CTO);
        verify(memberRepository).save(any(TeamMember.class));
    }

    @Test
    void updateMemberRole_whenSelfDemotion_shouldThrowBadRequestException() {
        sampleMember.setUserId(1L); // Member is the founder
        when(memberRepository.findById(200L)).thenReturn(Optional.of(sampleMember));
        RoleUpdateRequest request = RoleUpdateRequest.builder().role(TeamRole.CTO).build();

        assertThatThrownBy(() -> teamService.updateMemberRole(200L, request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Founders cannot update their own FOUNDER role");
    }

    @Test
    void updateMemberRole_whenForbiddenRole_shouldThrowBadRequestException() {
        when(memberRepository.findById(200L)).thenReturn(Optional.of(sampleMember));
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup);
        RoleUpdateRequest request = RoleUpdateRequest.builder().role(TeamRole.FOUNDER).build();

        assertThatThrownBy(() -> teamService.updateMemberRole(200L, request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only assign functional roles");
    }

    // -------------------------------------------------------------------------
    // getMyInvitations
    // -------------------------------------------------------------------------

    @Test
    void getMyInvitations_shouldReturnInvitations() {
        // given
        when(invitationRepository.findByInvitedUserIdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of(sampleInvitation));

        // when
        List<InvitationResponse> result = teamService.getMyInvitations(2L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvitedUserId()).isEqualTo(2L);
        verify(invitationRepository).findByInvitedUserIdOrderByCreatedAtDesc(2L);
    }

    // -------------------------------------------------------------------------
    // getTeamByStartup
    // -------------------------------------------------------------------------

    @Test
    void getTeamByStartup_whenValid_shouldEnrichWithUserDetails() {
        // given
        when(memberRepository.existsByStartupIdAndRole(10L, TeamRole.FOUNDER)).thenReturn(true);
        when(memberRepository.findByStartupId(10L)).thenReturn(List.of(
                TeamMember.builder().userId(1L).role(TeamRole.FOUNDER).build(),
                TeamMember.builder().userId(2L).role(TeamRole.CTO).build()
        ));
        when(userClient.getProfilesBatch("1,2")).thenReturn(List.of(
                UserDTO.builder().id(1L).name("Founder").build(),
                UserDTO.builder().id(2L).name("CTO").build()
        ));

        // when
        List<TeamMemberResponse> result = teamService.getTeamByStartup(10L);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserName()).isEqualTo("Founder");
        assertThat(result.get(1).getUserName()).isEqualTo("CTO");
    }

    @Test
    void getTeamByStartup_whenFounderMissing_shouldAutoAddFounder() {
        // given
        when(memberRepository.existsByStartupIdAndRole(10L, TeamRole.FOUNDER)).thenReturn(false);
        when(startupClient.getStartupById(10L)).thenReturn(sampleStartup); // founderId = 1L
        when(memberRepository.findByStartupId(10L)).thenReturn(List.of());

        // when
        teamService.getTeamByStartup(10L);

        // then
        verify(memberRepository).save(any());
    }

    @Test
    void getTeamByStartup_whenUserClientFails_shouldStillReturnMembers() {
        // given
        when(memberRepository.existsByStartupIdAndRole(10L, TeamRole.FOUNDER)).thenReturn(true);
        when(memberRepository.findByStartupId(10L)).thenReturn(List.of(sampleMember));
        when(userClient.getProfilesBatch(anyString())).thenThrow(new RuntimeException("API Down"));

        // when
        List<TeamMemberResponse> result = teamService.getTeamByStartup(10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserName()).isNull(); // enrichment failed but results returned
    }
}

