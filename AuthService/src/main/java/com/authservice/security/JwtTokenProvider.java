package com.authservice.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String ROLES_CLAIM = "roles";
    private static final String EMAIL_CLAIM = "email";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    private SecretKey secretKey;

    @PostConstruct
    void init() {
        // Если secret не в Base64, конвертируем строку в ключ
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException e) {
            // Если не Base64, используем строку напрямую
            keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        // Для HMAC нужен ключ минимум 256 бит (32 байта)
        if (keyBytes.length < 32) {
            // Дополняем до 32 байт
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /* =========================
       Token generation
       ========================= */

    public String generateAccessToken(
            UUID userId,
            String email,
            Set<String> roles
    ) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, email)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /* =========================
       Token validation
       ========================= */

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /* =========================
       Claims extraction
       ========================= */

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String getEmail(String token) {
        return parseClaims(token).get(EMAIL_CLAIM, String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> getRoles(String token) {
        return Set.copyOf(
                parseClaims(token).get(ROLES_CLAIM, Set.class)
        );
    }

    /* =========================
       Internal
       ========================= */

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
