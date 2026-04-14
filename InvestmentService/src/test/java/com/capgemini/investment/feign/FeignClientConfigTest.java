package com.capgemini.investment.feign;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignClientConfigTest {

    private final FeignClientConfig config = new FeignClientConfig();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void requestInterceptor_shouldForwardHeaders_whenAttributesExist() {
        // given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(request.getHeader("X-User-Id")).thenReturn("1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_INVESTOR");
        
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        
        RequestTemplate template = new RequestTemplate();
        
        // when
        config.requestInterceptor().apply(template);
        
        // then
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer token");
        assertThat(template.headers().get("X-User-Id")).containsExactly("1");
        assertThat(template.headers().get("X-User-Roles")).containsExactly("ROLE_INVESTOR");
    }

    @Test
    void requestInterceptor_shouldDoNothing_whenAttributesAreNull() {
        // given
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();
        
        // when
        config.requestInterceptor().apply(template);
        
        // then
        assertThat(template.headers()).isEmpty();
    }

    @Test
    void errorDecoder_shouldReturnInstance() {
        assertThat(config.errorDecoder()).isInstanceOf(FeignErrorDecoder.class);
    }
}
