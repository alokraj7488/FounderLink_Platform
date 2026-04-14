package com.capgemini.startup.mapper;

import com.capgemini.startup.dto.StartupResponse;
import com.capgemini.startup.entity.Startup;
import com.capgemini.startup.enums.StartupStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StartupMapperTest {

    private StartupMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new StartupMapper();
    }

    @Test
    void toResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Startup startup = Startup.builder()
                .id(1L).name("TechCo").description("A tech startup")
                .industry("Technology").problemStatement("Problem X")
                .solution("Solution Y").fundingGoal(BigDecimal.valueOf(100000))
                .stage(StartupStage.MVP).location("Bangalore")
                .founderId(10L).isApproved(true).isRejected(false)
                .createdAt(now).updatedAt(now)
                .build();

        StartupResponse response = mapper.toResponse(startup);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("TechCo");
        assertThat(response.getDescription()).isEqualTo("A tech startup");
        assertThat(response.getIndustry()).isEqualTo("Technology");
        assertThat(response.getProblemStatement()).isEqualTo("Problem X");
        assertThat(response.getSolution()).isEqualTo("Solution Y");
        assertThat(response.getFundingGoal()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(response.getStage()).isEqualTo(StartupStage.MVP);
        assertThat(response.getLocation()).isEqualTo("Bangalore");
        assertThat(response.getFounderId()).isEqualTo(10L);
        assertThat(response.getIsApproved()).isTrue();
        assertThat(response.getIsRejected()).isFalse();
    }

    @Test
    void toResponse_withNullOptionalFields_shouldMapAsNull() {
        Startup startup = Startup.builder()
                .id(1L).name("TechCo").founderId(10L)
                .isApproved(false).isRejected(false)
                .build();

        StartupResponse response = mapper.toResponse(startup);

        assertThat(response.getDescription()).isNull();
        assertThat(response.getLocation()).isNull();
        assertThat(response.getStage()).isNull();
    }
}
