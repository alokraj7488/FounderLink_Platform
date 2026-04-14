package com.capgemini.team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInviteSentEvent {
    private Long invitationId;
    private Long startupId;
    private Long invitedUserId;
    private String role;
}
