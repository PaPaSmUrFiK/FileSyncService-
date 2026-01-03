package com.authservice.repository;



import com.authservice.model.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Активные (не отозванные и не истёкшие) токены пользователя
     */
    @Query("""
        select rt from RefreshToken rt
        where rt.userId = :userId
          and rt.revoked = false
          and rt.expiresAt > :now
    """)
    List<RefreshToken> findActiveByUserId(UUID userId, LocalDateTime now);

    /**
     * Отзыв всех активных refresh-токенов пользователя
     */
    @Modifying
    @Query("""
        update RefreshToken rt
        set rt.revoked = true,
            rt.revokedAt = :revokedAt
        where rt.userId = :userId
          and rt.revoked = false
    """)
    int revokeAllByUserId(UUID userId, LocalDateTime revokedAt);

    /**
     * Отзыв одного токена
     */
    @Modifying
    @Query("""
        update RefreshToken rt
        set rt.revoked = true,
            rt.revokedAt = :revokedAt
        where rt.tokenHash = :tokenHash
    """)
    int revokeByTokenHash(String tokenHash, LocalDateTime revokedAt);
}
