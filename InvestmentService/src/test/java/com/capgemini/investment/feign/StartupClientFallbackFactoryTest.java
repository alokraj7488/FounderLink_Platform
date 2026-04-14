package com.capgemini.investment.feign;

import com.capgemini.investment.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupClientFallbackFactoryTest {

    private final StartupClientFallbackFactory factory = new StartupClientFallbackFactory();

    @Test
    void create_shouldReturnFallbackThatThrowsException() {
        // given
        Throwable cause = new RuntimeException("Service down");
        
        // when
        StartupClient fallback = factory.create(cause);
        
        // then
        assertThatThrownBy(() -> fallback.getStartupById(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unavailable");
    }
}
