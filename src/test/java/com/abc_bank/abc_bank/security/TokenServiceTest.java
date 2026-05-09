package com.abc_bank.abc_bank.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-for-hs256-signing";
    private static final String EMAIL = "alice@example.com";

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = newTokenService(SECRET, 60_000L);
    }

    private TokenService newTokenService(String secret, long expirationMs) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "JWT_SECRETE", secret);
        ReflectionTestUtils.setField(service, "EXPIRATION_TIME", expirationMs);
        ReflectionTestUtils.invokeMethod(service, "init");
        return service;
    }

    private UserDetails userDetailsFor(String email) {
        return new User(email, "irrelevant", Collections.emptyList());
    }

    @Test
    void generateToken_producesParseableTokenWithSubject() {
        String token = tokenService.generateToken(EMAIL);

        assertThat(token).isNotBlank();
        assertThat(tokenService.getUsernameFromToken(token)).isEqualTo(EMAIL);
    }

    @Test
    void isTokenValid_returnsTrueForFreshTokenAndMatchingUser() {
        String token = tokenService.generateToken(EMAIL);

        assertThat(tokenService.isTokenValid(token, userDetailsFor(EMAIL))).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseWhenSubjectDoesNotMatchUserDetails() {
        String token = tokenService.generateToken(EMAIL);

        assertThat(tokenService.isTokenValid(token, userDetailsFor("someone-else@example.com"))).isFalse();
    }

    @Test
    void getUsernameFromToken_throwsWhenSignatureWasMintedWithDifferentKey() {
        TokenService otherService = newTokenService(
                "another-secret-key-also-at-least-32-bytes-long-padding",
                60_000L);
        String tokenSignedWithOtherKey = otherService.generateToken(EMAIL);

        assertThatThrownBy(() -> tokenService.getUsernameFromToken(tokenSignedWithOtherKey))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void getUsernameFromToken_throwsForMalformedToken() {
        assertThatThrownBy(() -> tokenService.getUsernameFromToken("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void isTokenValid_throwsForExpiredToken() {
        TokenService expiredService = newTokenService(SECRET, -1_000L);
        String expiredToken = expiredService.generateToken(EMAIL);

        assertThatThrownBy(() -> tokenService.isTokenValid(expiredToken, userDetailsFor(EMAIL)))
                .isInstanceOf(JwtException.class);
    }
}
