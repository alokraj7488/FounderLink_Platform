package com.capgemini.notification.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Event fired when a startup is initially created
public class StartupCreatedEvent {
    private Long startupId;
    private Long founderId;
    private String startupName;
    private String industry;
    private BigDecimal fundingGoal;
}
