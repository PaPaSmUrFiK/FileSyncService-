package com.authservice.service;


import com.authservice.model.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TokenService {

    /**
     * Создаёт refresh token (raw + hash) и сохраняет в БД.
     */
    String createRefreshToken(UUID userId, String deviceInfo);

    /**
     * Проверяет refresh token и возвращает связанную запись.
     */
    RefreshToken validateRefreshToken(String rawRefreshToken);

    /**
     * Отзывает один refresh token.
     */
    void revokeRefreshToken(String rawRefreshToken);

    /**
     * Отзывает все refresh token пользователя.
     */
    void revokeAllUserTokens(UUID userId);
}