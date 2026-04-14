package com.capgemini.team.dto;

import java.time.LocalDateTime;

import com.capgemini.team.enums.InvitationStatus;
import com.capgemini.team.enums.TeamRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvitationResponse {

    private Long id;
    private Long startupId;
    private Long invitedUserId;
    private String startupName;
    private String startupIndustry;
    private TeamRole role;
    private InvitationStatus status;
    private LocalDateTime createdAt;
}
