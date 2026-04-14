package com.capgemini.team.feign;

import com.capgemini.team.exception.ResourceNotFoundException;
import com.capgemini.team.exception.ServiceUnavailableException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeignTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();
    private final StartupClientFallbackFactory startupFallbackFactory = new StartupClientFallbackFactory();
    private final UserClientFallbackFactory userFallbackFactory = new UserClientFallbackFactory();

    @Test
    void feignErrorDecoder_when404_shouldReturnResourceNotFoundException() {
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(mockRequest())
                .headers(new HashMap<>())
                .build();

        Exception exception = decoder.decode("testMethod", response);

        assertThat(exception).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Requested resource not found");
    }

    @Test
    void feignErrorDecoder_when500_shouldReturnDefaultException() {
        Response response = Response.builder()
                .status(500)
                .reason("Internal Error")
                .request(mockRequest())
                .headers(new HashMap<>())
                .build();

        Exception exception = decoder.decode("testMethod", response);

        // Default decoder returns a FeignException or RetryableException
        assertThat(exception).isNotInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startupClientFallbackFactory_shouldThrowServiceUnavailable() {
        StartupClient fallback = startupFallbackFactory.create(new RuntimeException("fallback test"));
        
        assertThatThrownBy(() -> fallback.getStartupById(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Startup service is currently unavailable");
    }

    @Test
    void userClientFallbackFactory_shouldThrowServiceUnavailable() {
        UserClient fallback = userFallbackFactory.create(new RuntimeException("fallback test"));
        
        assertThatThrownBy(() -> fallback.getUserById(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("User service is currently unavailable");

        assertThatThrownBy(() -> fallback.getProfilesBatch("1,2"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("User service is currently unavailable");
    }

    private Request mockRequest() {
        return Request.create(Request.HttpMethod.GET, "/test", new HashMap<>(), null, StandardCharsets.UTF_8, null);
    }
}
