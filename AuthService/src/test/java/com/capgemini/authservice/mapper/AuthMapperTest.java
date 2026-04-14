package com.capgemini.authservice.mapper;

import com.capgemini.authservice.dto.UserSummaryDto;
import com.capgemini.authservice.entity.RoleEntity;
import com.capgemini.authservice.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMapperTest {

    private AuthMapper authMapper;

    @BeforeEach
    void setUp() {
        authMapper = new AuthMapper();
    }

    @Test
    void toUserSummaryDto_shouldMapAllFields() {
        RoleEntity role = RoleEntity.builder().id(1L).name("ROLE_FOUNDER").build();
        UserEntity user = UserEntity.builder()
                .id(10L)
                .name("Alice")
                .email("alice@example.com")
                .roles(Set.of(role))
                .build();

        UserSummaryDto dto = authMapper.toUserSummaryDto(user);

        assertThat(dto.getUserId()).isEqualTo(10L);
        assertThat(dto.getName()).isEqualTo("Alice");
        assertThat(dto.getEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getRole()).isEqualTo("ROLE_FOUNDER");
    }

    @Test
    void toUserSummaryDto_whenNoRoles_shouldReturnNullRole() {
        UserEntity user = UserEntity.builder()
                .id(10L)
                .name("Alice")
                .email("alice@example.com")
                .roles(Collections.emptySet())
                .build();

        UserSummaryDto dto = authMapper.toUserSummaryDto(user);

        assertThat(dto.getUserId()).isEqualTo(10L);
        assertThat(dto.getRole()).isNull();
    }

    @Test
    void toUserSummaryDto_withMultipleRoles_shouldReturnFirstRole() {
        RoleEntity role1 = RoleEntity.builder().id(1L).name("ROLE_FOUNDER").build();
        RoleEntity role2 = RoleEntity.builder().id(2L).name("ROLE_ADMIN").build();
        UserEntity user = UserEntity.builder()
                .id(10L)
                .name("Alice")
                .email("alice@example.com")
                .roles(Set.of(role1, role2))
                .build();

        UserSummaryDto dto = authMapper.toUserSummaryDto(user);

        assertThat(dto.getRole()).isNotNull();
        assertThat(dto.getRole()).isIn("ROLE_FOUNDER", "ROLE_ADMIN");
    }
}
