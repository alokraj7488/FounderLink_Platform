package com.capgemini.team.feign;

import com.capgemini.team.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupClientFallbackFactoryTest {

    private final StartupClientFallbackFactory factory = new StartupClientFallbackFactory();

    @Test
    void create_shouldReturnFallbackClient() {
        StartupClient fallback = factory.create(new RuntimeException("Connection refused"));
        assertThat(fallback).isNotNull();
    }

    @Test
    void fallbackClient_shouldThrowServiceUnavailableException() {
        StartupClient fallback = factory.create(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> fallback.getStartupById(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("currently unavailable");
    }
}
