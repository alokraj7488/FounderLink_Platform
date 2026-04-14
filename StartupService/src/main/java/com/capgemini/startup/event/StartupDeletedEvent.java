package com.capgemini.startup.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Event fired when a startup is deleted to help other services clean up their data
public class StartupDeletedEvent {
    private Long startupId;
    private String startupName;
    private Long founderId;
}
