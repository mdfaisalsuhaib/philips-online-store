package com.philips.onlinestore.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-signing-key-with-at-least-32-bytes".getBytes()
    );

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60_000);
    }

    @Test
    void generatesSignedTokenWithSubjectAndExpiration() {
        String token = jwtService.generateToken("customer@example.com");

        assertNotNull(token);
        assertEquals("customer@example.com", jwtService.extractSubject(token));
        assertTrue(jwtService.isTokenValid(token));
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        assertEquals(60, claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond());
        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1])
        );
        assertFalse(payload.contains("password"));
        assertFalse(payload.contains("hash"));
    }

    @Test
    void extractsSubjectFromValidToken() {
        String token = jwtService.generateToken("user-123");

        assertEquals("user-123", jwtService.extractSubject(token));
    }

    @Test
    void rejectsExpiredToken() {
        Instant expiredAt = Instant.now().minusSeconds(60);
        String token = Jwts.builder()
                .subject("customer@example.com")
                .issuedAt(Date.from(expiredAt.minusSeconds(60)))
                .expiration(Date.from(expiredAt))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String otherSecret = Base64.getEncoder().encodeToString(
                "different-signing-key-with-at-least-32-bytes".getBytes()
        );
        String token = Jwts.builder()
                .subject("customer@example.com")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(otherSecret)), Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void rejectsSecretsShorterThanHs256Minimum() {
        String weakSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThrows(IllegalArgumentException.class, () -> new JwtService(weakSecret, 60_000));
    }
}
