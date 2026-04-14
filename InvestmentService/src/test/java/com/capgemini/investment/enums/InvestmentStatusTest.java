package com.capgemini.investment.enums;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InvestmentStatusTest {

    @Test
    void enumValues_shouldBePresent() {
        assertThat(InvestmentStatus.valueOf("PENDING")).isEqualTo(InvestmentStatus.PENDING);
        assertThat(InvestmentStatus.valueOf("APPROVED")).isEqualTo(InvestmentStatus.APPROVED);
        assertThat(InvestmentStatus.valueOf("REJECTED")).isEqualTo(InvestmentStatus.REJECTED);
    }
}
