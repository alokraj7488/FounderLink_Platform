package com.capgemini.investment.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCreatedEvent {
    private Long investmentId;
    private Long investorId;
    private Long startupId;
    private Long founderId;
    private BigDecimal amount;
    private String startupName;
}
