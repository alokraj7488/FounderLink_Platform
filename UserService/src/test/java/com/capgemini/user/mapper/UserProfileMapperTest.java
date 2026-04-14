package com.capgemini.user.mapper;

import com.capgemini.user.dto.UserProfileResponse;
import com.capgemini.user.entity.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileMapperTest {

    private final UserProfileMapper mapper = new UserProfileMapper();

    @Test
    void toResponse_shouldMapAllFields() {
        // given
        LocalDateTime now = LocalDateTime.now();
        UserProfile profile = UserProfile.builder()
                .id(100L)
                .userId(1L)
                .name("Alice")
                .email("alice@example.com")
                .bio("Bio")
                .role("ROLE_ADMIN")
                .skills("Java")
                .experience("Expert")
                .portfolioLinks("http://links.com")
                .createdAt(now)
                .updatedAt(now)
                .build();

        // when
        UserProfileResponse response = mapper.toResponse(profile);

        // then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getBio()).isEqualTo("Bio");
        assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(response.getSkills()).isEqualTo("Java");
        assertThat(response.getExperience()).isEqualTo("Expert");
        assertThat(response.getPortfolioLinks()).isEqualTo("http://links.com");
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
    }
}
