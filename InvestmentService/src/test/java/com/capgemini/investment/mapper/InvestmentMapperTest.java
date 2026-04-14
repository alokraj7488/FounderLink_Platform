package com.capgemini.investment.mapper;

import com.capgemini.investment.dto.InvestmentResponse;
import com.capgemini.investment.entity.Investment;
import com.capgemini.investment.enums.InvestmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentMapperTest {

    private InvestmentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InvestmentMapper();
    }

    @Test
    void toResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Investment investment = Investment.builder()
                .id(1L)
                .startupId(100L)
                .investorId(200L)
                .amount(BigDecimal.valueOf(50000))
                .status(InvestmentStatus.PENDING)
                .createdAt(now)
                .build();

        InvestmentResponse response = mapper.toResponse(investment);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStartupId()).isEqualTo(100L);
        assertThat(response.getInvestorId()).isEqualTo(200L);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(response.getStatus()).isEqualTo(InvestmentStatus.PENDING);
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toResponse_withApprovedStatus_shouldMapCorrectly() {
        Investment investment = Investment.builder()
                .id(2L).startupId(100L).investorId(200L)
                .amount(BigDecimal.valueOf(100000))
                .status(InvestmentStatus.APPROVED)
                .build();

        InvestmentResponse response = mapper.toResponse(investment);

        assertThat(response.getStatus()).isEqualTo(InvestmentStatus.APPROVED);
    }
}
