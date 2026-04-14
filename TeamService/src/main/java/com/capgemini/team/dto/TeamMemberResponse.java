package com.capgemini.team.dto;

import java.time.LocalDateTime;

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
public class TeamMemberResponse {

    private Long id;
    private Long startupId;
    private Long userId;
    private String startupName;
    private String userName;
    private String userEmail;
    private TeamRole role;
    private LocalDateTime joinedAt;
}
