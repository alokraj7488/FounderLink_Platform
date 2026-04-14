package com.capgemini.authservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // A valid Base64-encoded 256-bit key for HMAC-SHA
    private static final String TEST_SECRET =
            "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, 86400000L, 604800000L);
    }

    @Test
    void generateAccessToken_shouldReturnNonNullToken() {
        String token = jwtUtil.generateAccessToken(1L, "alice@example.com", "FOUNDER");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateRefreshToken_shouldReturnNonNullToken() {
        String token = jwtUtil.generateRefreshToken(1L, "alice@example.com");
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = jwtUtil.generateAccessToken(1L, "alice@example.com", "FOUNDER");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withInvalidToken_shouldReturnFalse() {
        assertThat(jwtUtil.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_withNullToken_shouldReturnFalse() {
        assertThat(jwtUtil.validateToken(null)).isFalse();
    }

    @Test
    void extractUserId_shouldReturnCorrectUserId() {
        String token = jwtUtil.generateAccessToken(42L, "bob@example.com", "INVESTOR");
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String token = jwtUtil.generateAccessToken(1L, "bob@example.com", "INVESTOR");
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("bob@example.com");
    }

    @Test
    void extractRole_shouldReturnCorrectRole() {
        String token = jwtUtil.generateAccessToken(1L, "alice@example.com", "FOUNDER");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("FOUNDER");
    }

    @Test
    void extractRole_fromRefreshToken_shouldReturnNull() {
        String token = jwtUtil.generateRefreshToken(1L, "alice@example.com");
        assertThat(jwtUtil.extractRole(token)).isNull();
    }

    @Test
    void extractRole_withEmptyRolesList_shouldReturnNull() {
        // Re-generate manually with empty list
        String email = "empty@example.com";
        String emptyRolesToken = io.jsonwebtoken.Jwts.builder()
                .claims(java.util.Map.of("userId", 1, "roles", java.util.List.of()))
                .subject(email)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(TEST_SECRET)))
                .compact();
        
        assertThat(jwtUtil.extractRole(emptyRolesToken)).isNull();
    }

    @Test
    void validateToken_withExpiredToken_shouldReturnFalse() {
        // Set expiration to 0ms (already expired)
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 0L);
        String token = jwtUtil.generateAccessToken(1L, "alice@example.com", "FOUNDER");
        // Token issued at now with 0ms expiration is already expired
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    void differentTokensForDifferentUsers_shouldNotBeEqual() {
        String token1 = jwtUtil.generateAccessToken(1L, "alice@example.com", "FOUNDER");
        String token2 = jwtUtil.generateAccessToken(2L, "bob@example.com", "INVESTOR");
        assertThat(token1).isNotEqualTo(token2);
    }
}
