package com.capgemini.investment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.capgemini.investment.enums.InvestmentStatus;

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
public class InvestmentResponse {

    private Long id;
    private Long startupId;
    private Long investorId;
    private String startupName;
    private String startupIndustry;
    private BigDecimal amount;
    private InvestmentStatus status;
    private LocalDateTime createdAt;
}
