package com.capgemini.startup.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Event fired when an Admin rejects a startup
public class StartupRejectedEvent {
    private Long startupId;
    private Long founderId;
    private String startupName;
}
