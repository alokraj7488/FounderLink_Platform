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
public class InvestmentApprovedEvent {
    private Long investmentId;
    private Long investorId;
    private Long startupId;
    private BigDecimal amount;
}
