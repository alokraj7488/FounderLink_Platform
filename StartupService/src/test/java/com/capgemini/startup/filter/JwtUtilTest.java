package com.capgemini.startup.filter;

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

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    private String generateTestToken(Long userId, List<String> roles, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .claims(Map.of("userId", userId, "roles", roles))
                .subject("test@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = generateTestToken(1L, List.of("ROLE_FOUNDER"), 86400000L);
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
        String token = generateTestToken(42L, List.of("ROLE_INVESTOR"), 86400000L);
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void extractRoles_shouldReturnCorrectRoles() {
        String token = generateTestToken(1L, List.of("ROLE_FOUNDER", "ROLE_ADMIN"), 86400000L);
        assertThat(jwtUtil.extractRoles(token)).containsExactly("ROLE_FOUNDER", "ROLE_ADMIN");
    }
}

