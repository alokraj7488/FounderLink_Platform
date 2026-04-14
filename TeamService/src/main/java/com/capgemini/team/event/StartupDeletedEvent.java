package com.capgemini.team.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Received when a startup is deleted elsewhere; used to trigger local cleanup
public class StartupDeletedEvent {
    private Long id;
    private String name;
    private Long founderId;
}
