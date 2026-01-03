package com.authservice.service;

import com.authservice.exception.TokenException;
import com.authservice.model.domain.RefreshToken;
import com.authservice.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TokenServiceImpl implements TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationSeconds;

    /* =========================
       Public API
       ========================= */

    @Override
    public String createRefreshToken(UUID userId, String deviceInfo) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSeconds))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Override
    public RefreshToken validateRefreshToken(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenException("Refresh token not found"));

        if (token.getRevoked()) {
            throw new TokenException("Refresh token revoked");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenException("Refresh token expired");
        }

        return token;
    }

    @Override
    public void revokeRefreshToken(String rawRefreshToken) {
        String tokenHash = hash(rawRefreshToken);

        int updated = refreshTokenRepository.revokeByTokenHash(
                tokenHash,
                LocalDateTime.now()
        );

        if (updated == 0) {
            throw new TokenException("Refresh token not found");
        }
    }

    @Override
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(
                userId,
                LocalDateTime.now()
        );
    }

    /* =========================
       Internal helpers
       ========================= */

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }
}
