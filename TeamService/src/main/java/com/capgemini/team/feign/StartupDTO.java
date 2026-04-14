package com.capgemini.team.feign;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartupDTO {

    private Long id;
    private String name;
    private String description;
    private String industry;
    private String problemStatement;
    private String solution;
    private BigDecimal fundingGoal;
    private String stage;
    private String location;
    private Long founderId;
    private Boolean isApproved;
    private Boolean isRejected;
}
