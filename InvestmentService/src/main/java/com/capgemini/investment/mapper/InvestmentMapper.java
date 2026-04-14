package com.capgemini.investment.mapper;

import com.capgemini.investment.dto.InvestmentResponse;
import com.capgemini.investment.entity.Investment;
import com.capgemini.investment.feign.StartupDTO;
import org.springframework.stereotype.Component;

@Component
public class InvestmentMapper {

    public InvestmentResponse toResponse(Investment investment) {
        return InvestmentResponse.builder()
                .id(investment.getId())
                .startupId(investment.getStartupId())
                .investorId(investment.getInvestorId())
                .amount(investment.getAmount())
                .status(investment.getStatus())
                .createdAt(investment.getCreatedAt())
                .build();
    }

    public InvestmentResponse toResponse(Investment investment, StartupDTO startup) {
        InvestmentResponse response = toResponse(investment);
        if (startup != null) {
            response.setStartupName(startup.getName());
            response.setStartupIndustry(startup.getIndustry());
        }
        return response;
    }
}
