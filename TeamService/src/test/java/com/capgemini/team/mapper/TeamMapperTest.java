package com.capgemini.team.mapper;

import com.capgemini.team.dto.InvitationResponse;
import com.capgemini.team.dto.TeamMemberResponse;
import com.capgemini.team.entity.TeamInvitation;
import com.capgemini.team.entity.TeamMember;
import com.capgemini.team.enums.InvitationStatus;
import com.capgemini.team.enums.TeamRole;
import com.capgemini.team.feign.StartupDTO;
import com.capgemini.team.feign.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMapperTest {

    private TeamMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TeamMapper();
    }

    @Test
    void toInvitationResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        TeamInvitation invitation = TeamInvitation.builder()
                .id(1L).startupId(100L).invitedUserId(200L)
                .role(TeamRole.CTO).status(InvitationStatus.PENDING)
                .createdAt(now).build();

        InvitationResponse response = mapper.toInvitationResponse(invitation);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStartupId()).isEqualTo(100L);
        assertThat(response.getInvitedUserId()).isEqualTo(200L);
        assertThat(response.getRole()).isEqualTo(TeamRole.CTO);
        assertThat(response.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toMemberResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        TeamMember member = TeamMember.builder()
                .id(1L).startupId(100L).userId(200L)
                .role(TeamRole.FOUNDER).joinedAt(now).build();

        TeamMemberResponse response = mapper.toMemberResponse(member);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStartupId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(200L);
        assertThat(response.getRole()).isEqualTo(TeamRole.FOUNDER);
        assertThat(response.getJoinedAt()).isEqualTo(now);
    }

    @Test
    void toInvitationResponse_withStartup_shouldMapEnrichedFields() {
        TeamInvitation invitation = TeamInvitation.builder()
                .id(1L).startupId(100L).invitedUserId(200L)
                .role(TeamRole.CTO).status(InvitationStatus.PENDING).build();
        StartupDTO startup = StartupDTO.builder()
                .id(100L).name("Tech Startup").industry("FinTech").build();

        InvitationResponse response = mapper.toInvitationResponse(invitation, startup);

        assertThat(response.getStartupName()).isEqualTo("Tech Startup");
        assertThat(response.getStartupIndustry()).isEqualTo("FinTech");
    }

    @Test
    void toMemberResponse_withDetails_shouldMapEnrichedFields() {
        TeamMember member = TeamMember.builder()
                .id(1L).startupId(100L).userId(200L)
                .role(TeamRole.FOUNDER).build();
        StartupDTO startup = StartupDTO.builder().id(100L).name("Tech Startup").build();
        UserDTO user = UserDTO.builder().id(200L).name("Jane Doe").email("jane@example.com").build();

        TeamMemberResponse response = mapper.toMemberResponse(member, startup, user);

        assertThat(response.getStartupName()).isEqualTo("Tech Startup");
        assertThat(response.getUserName()).isEqualTo("Jane Doe");
        assertThat(response.getUserEmail()).isEqualTo("jane@example.com");
    }
}

