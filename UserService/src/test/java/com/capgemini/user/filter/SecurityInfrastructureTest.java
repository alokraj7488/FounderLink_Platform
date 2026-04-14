package com.capgemini.user.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityInfrastructureTest {

    private String secret = Base64.getEncoder().encodeToString(new byte[32]);
    private JwtUtil jwtUtil;
    private JwtAuthenticationFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtUtil = new JwtUtil(secret);
        filter = new JwtAuthenticationFilter(jwtUtil);
    }

    @Test
    void filter_whenHeadersPresent_shouldSetAuthentication() throws Exception {
        when(request.getHeader("X-User-Id")).thenReturn("10");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_ADMIN,ROLE_USER");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("10");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_whenJwtPresent_shouldSetAuthentication() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        String token = Jwts.builder()
                .claims(Map.of("userId", 10L, "roles", List.of("ROLE_USER")))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60))
                .signWith(key)
                .compact();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("10");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void jwtUtil_validateToken_whenExpired_shouldReturnFalse() {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        String token = Jwts.builder()
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    void jwtUtil_validateToken_whenInvalid_shouldReturnFalse() {
        assertThat(jwtUtil.validateToken("invalid-token")).isFalse();
    }
}
