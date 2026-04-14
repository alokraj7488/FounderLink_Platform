package com.capgemini.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filterFactory;
    private GatewayFilterChain filterChain;

    private static final String TEST_SECRET =
            "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";

    @BeforeEach
    void setUp() {
        filterFactory = new JwtAuthenticationFilter(TEST_SECRET);
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String generateValidToken() {
        return generateTokenWithExpiration(86400000L);
    }

    private String generateTokenWithExpiration(long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .claims(Map.of("userId", 42, "roles", List.of("ROLE_FOUNDER")))
                .subject("test@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    void apply_withPublicPath_shouldProceedWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void apply_withOptionsMethod_shouldProceedWithoutAuth() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/api/secure").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void apply_withNoAuthHeader_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/secure").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apply_withInvalidToken_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/secure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apply_withExpiredToken_shouldReturnUnauthorized() {
        String token = generateTokenWithExpiration(-10000L); // 10s ago
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/secure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apply_withMalformedHeader_shouldReturnUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/secure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer") // Missing space and token
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain, never()).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apply_withValidToken_shouldMutateRequestAndProceed() {
        String token = generateValidToken();
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/secure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilter filter = filterFactory.apply(new JwtAuthenticationFilter.Config());
        filter.filter(exchange, filterChain).block();

        verify(filterChain).filter(argThat(mutatedExchange -> {
            HttpHeaders headers = mutatedExchange.getRequest().getHeaders();
            return headers.getFirst("X-User-Id").equals("42") &&
                   headers.getFirst("X-User-Roles").equals("ROLE_FOUNDER");
        }));
    }
}
