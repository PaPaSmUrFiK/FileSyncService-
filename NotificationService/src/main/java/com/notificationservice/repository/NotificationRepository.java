package com.notificationservice.repository;

import com.notificationservice.model.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface NotificationRepository extends ReactiveSortingRepository<Notification, UUID> {

    Flux<Notification> findByUserId(UUID userId, Pageable pageable);

    Flux<Notification> findByUserIdAndRead(UUID userId, boolean read, Pageable pageable);

    Flux<Notification> findByUserIdAndNotificationType(UUID userId, String type, Pageable pageable);

    Mono<Boolean> existsByUserIdAndResourceIdAndNotificationType(UUID userId, UUID resourceId, String notificationType);

    Mono<Long> countByUserIdAndRead(UUID userId, boolean read);

    Mono<Void> deleteByIdAndUserId(UUID id, UUID userId);

    Mono<Notification> save(Notification notification);

    @org.springframework.data.r2dbc.repository.Modifying
    @Query("UPDATE notifications SET is_read = true, read_at = CURRENT_TIMESTAMP WHERE user_id = :userId AND is_read = false")
    Mono<Void> markAllAsReadForUser(UUID userId);

    @org.springframework.data.r2dbc.repository.Modifying
    @Query("UPDATE notifications SET is_read = true, read_at = CURRENT_TIMESTAMP WHERE id = :id AND user_id = :userId")
    Mono<Void> markAsRead(UUID id, UUID userId);

    @org.springframework.data.r2dbc.repository.Modifying
    @Query("DELETE FROM notifications WHERE user_id = :userId")
    Mono<Void> deleteAllByUserId(UUID userId);
}
