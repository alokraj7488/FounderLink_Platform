package com.capgemini.investment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Received when an Admin rejects a startup request
public class StartupRejectedEvent {
    private Long startupId;
    private Long founderId;
    private String startupName;
}
