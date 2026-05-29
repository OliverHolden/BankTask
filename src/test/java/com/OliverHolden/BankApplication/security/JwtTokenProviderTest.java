package com.OliverHolden.BankApplication.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long";
    private static final long EXPIRATION_MS = 3_600_000L;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonNullToken() {
        JwtTokenProvider.TokenResult result = jwtTokenProvider.generateToken("usr-abc123");

        assertThat(result.token()).isNotNull().isNotBlank();
    }

    @Test
    void generateToken_expiresAtIsInTheFuture() {
        JwtTokenProvider.TokenResult result = jwtTokenProvider.generateToken("usr-abc123");

        assertThat(result.expiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void generateToken_expiresAtMatchesConfiguredExpiry() {
        JwtTokenProvider.TokenResult result = jwtTokenProvider.generateToken("usr-abc123");

        OffsetDateTime expectedExpiry = OffsetDateTime.now().plusSeconds(EXPIRATION_MS / 1000);
        assertThat(result.expiresAt()).isCloseTo(expectedExpiry, within(5, ChronoUnit.SECONDS));
    }

    @Test
    void validateAndDecode_validToken_returnsJwt() {
        String token = jwtTokenProvider.generateToken("usr-abc123").token();

        Jwt jwt = jwtTokenProvider.validateAndDecode(token);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("usr-abc123");
    }

    @Test
    void validateAndDecode_malformedToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtTokenProvider.validateAndDecode("not.a.valid.token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndDecode_tokenSignedWithDifferentSecret_throwsJwtException() {
        JwtTokenProvider otherProvider = new JwtTokenProvider("different-secret-key-that-is-also-long-enough", EXPIRATION_MS);
        String tokenFromOtherProvider = otherProvider.generateToken("usr-abc123").token();

        assertThatThrownBy(() -> jwtTokenProvider.validateAndDecode(tokenFromOtherProvider))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndDecode_expiredToken_throwsJwtException() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, 1L);
        String expiredToken = shortLivedProvider.generateToken("usr-abc123").token();

        assertThatThrownBy(() -> jwtTokenProvider.validateAndDecode(expiredToken))
                .isInstanceOf(JwtException.class);
    }
}
