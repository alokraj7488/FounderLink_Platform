package com.capgemini.investment.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String TEST_SECRET =
            "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private static final long DEFAULT_EXPIRATION = 86400000L;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    private String generateTestToken(Long userId, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .claims(Map.of("userId", userId, "roles", roles))
                .subject("test@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + DEFAULT_EXPIRATION))
                .signWith(key)
                .compact();
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = generateTestToken(1L, List.of("ROLE_FOUNDER"));
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withInvalidToken_shouldReturnFalse() {
        assertThat(jwtUtil.validateToken("garbage.token.here")).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_shouldReturnFalse() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        String token = Jwts.builder()
                .claims(Map.of("userId", 1L))
                .subject("test@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 20000))
                .expiration(new Date(System.currentTimeMillis() - 10000))
                .signWith(key)
                .compact();
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    void extractUserId_shouldReturnCorrectId() {
        String token = generateTestToken(42L, List.of("ROLE_INVESTOR"));
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void extractRoles_shouldReturnCorrectRoles() {
        String token = generateTestToken(1L, List.of("ROLE_FOUNDER", "ROLE_ADMIN"));
        assertThat(jwtUtil.extractRoles(token)).containsExactly("ROLE_FOUNDER", "ROLE_ADMIN");
    }
}

