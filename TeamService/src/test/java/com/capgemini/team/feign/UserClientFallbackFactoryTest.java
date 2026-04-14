package com.capgemini.team.feign;

import com.capgemini.team.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserClientFallbackFactoryTest {

    private final UserClientFallbackFactory factory = new UserClientFallbackFactory();

    @Test
    void create_shouldReturnFallbackClient() {
        UserClient fallback = factory.create(new RuntimeException("Connection refused"));
        assertThat(fallback).isNotNull();
    }

    @Test
    void fallbackClient_shouldThrowServiceUnavailableException() {
        UserClient fallback = factory.create(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> fallback.getUserById(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("currently unavailable");
    }
}
