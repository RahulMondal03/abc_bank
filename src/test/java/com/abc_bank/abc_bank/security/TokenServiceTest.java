package com.abc_bank.abc_bank.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long-1234567890";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "JWT_SECRETE", SECRET);
        ReflectionTestUtils.setField(tokenService, "EXPIRATION_TIME", ONE_HOUR_MS);
        ReflectionTestUtils.invokeMethod(tokenService, "init");
    }

    private UserDetails userDetails(String email) {
        return new User(email, "n/a", Collections.emptyList());
    }

    @Test
    void generateToken_producesParsableJwtWithEmailAsSubject() {
        String token = tokenService.generateToken("alice@example.com");

        assertThat(token).isNotBlank();
        assertThat(tokenService.getUsernameFromToken(token)).isEqualTo("alice@example.com");
    }

    @Test
    void isTokenValid_returnsTrueForFreshTokenWithMatchingUser() {
        String token = tokenService.generateToken("alice@example.com");

        assertThat(tokenService.isTokenValid(token, userDetails("alice@example.com"))).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForMismatchedUser() {
        String token = tokenService.generateToken("alice@example.com");

        assertThat(tokenService.isTokenValid(token, userDetails("bob@example.com"))).isFalse();
    }

    @Test
    void getUsernameFromToken_throwsOnTamperedSignature() {
        String token = tokenService.generateToken("alice@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> tokenService.getUsernameFromToken(tampered))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void isTokenValid_throwsExpiredJwtExceptionForExpiredToken() {
        TokenService shortLived = new TokenService();
        ReflectionTestUtils.setField(shortLived, "JWT_SECRETE", SECRET);
        ReflectionTestUtils.setField(shortLived, "EXPIRATION_TIME", 1L);
        ReflectionTestUtils.invokeMethod(shortLived, "init");

        String token = shortLived.generateToken("alice@example.com");
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThatThrownBy(() -> shortLived.isTokenValid(token, userDetails("alice@example.com")))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void generateToken_producesDifferentTokensForDifferentSubjects() {
        String alice = tokenService.generateToken("alice@example.com");
        String bob = tokenService.generateToken("bob@example.com");

        assertThat(alice).isNotEqualTo(bob);
        assertThat(tokenService.getUsernameFromToken(alice)).isEqualTo("alice@example.com");
        assertThat(tokenService.getUsernameFromToken(bob)).isEqualTo("bob@example.com");
    }
}
