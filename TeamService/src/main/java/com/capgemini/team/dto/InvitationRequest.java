package com.capgemini.team.dto;

import com.capgemini.team.enums.TeamRole;
import jakarta.validation.constraints.NotNull;
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
public class InvitationRequest {

    @NotNull(message = "Startup ID is required")
    private Long startupId;

    @NotNull(message = "Invited user ID is required")
    private Long invitedUserId;

    @NotNull(message = "Role is required")
    private TeamRole role;
}
