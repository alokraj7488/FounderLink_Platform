package com.capgemini.apigateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private WebFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        filterChain = mock(WebFilterChain.class);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void filter_shouldAddCorrelationIdHeaderAndCompleteSuccessfully() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, filterChain);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        verify(filterChain).filter(argThat(mutatedExchange -> 
            mutatedExchange.getRequest().getHeaders().containsKey("X-Correlation-Id")
        ));
    }
}
