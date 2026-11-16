package com.philips.onlinestore.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET must be a non-empty Base64-encoded value.");
        }

        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(base64Secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64.", exception);
        }
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes for HS256.");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION_MS must be greater than zero.");
        }

        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank.");
        }

        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusMillis(expirationMs)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extractSubject(String token) {
        Claims claims = parseClaims(token);
        return claims.getSubject();
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            String subject = extractSubject(token);
            return subject != null && !subject.isBlank();
        } catch (ExpiredJwtException exception) {
            log.warn("JWT validation failed: reason=expired");
        } catch (PrematureJwtException exception) {
            log.warn("JWT validation failed: reason=not_yet_valid");
        } catch (SignatureException exception) {
            log.warn("JWT validation failed: reason=signature_invalid");
        } catch (MalformedJwtException exception) {
            log.warn("JWT validation failed: reason=malformed");
        } catch (UnsupportedJwtException exception) {
            log.warn("JWT validation failed: reason=unsupported");
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("JWT validation failed: reason=invalid");
        }
        return false;
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT must not be blank.");
        }

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
