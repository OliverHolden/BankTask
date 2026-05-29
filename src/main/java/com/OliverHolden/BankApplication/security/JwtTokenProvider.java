package com.OliverHolden.BankApplication.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final long expirationMs;

    public record TokenResult(String token, OffsetDateTime expiresAt) {}

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.expirationMs = expirationMs;
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key.getEncoded()));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
    }

    public TokenResult generateToken(String userId) {
        log.info("Generating JWT for userId: {}", userId);
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now)
                .expiresAt(expiry)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResult(token, expiry.atOffset(ZoneOffset.UTC));
    }

    public Jwt validateAndDecode(String token) {
        log.debug("Validating JWT token");
        return jwtDecoder.decode(token);
    }
}
