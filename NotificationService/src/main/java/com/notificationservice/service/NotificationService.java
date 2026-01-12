package com.notificationservice.service;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.model.domain.NotificationPreference;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    Mono<Void> sendNotification(UUID userId, String type, String title, String message, String priority,
            Map<String, String> data, List<String> channels);

    Flux<Notification> getNotifications(UUID userId, boolean unreadOnly, String type, int limit, int offset);

    Mono<Long> getUnreadCount(UUID userId);

    Mono<Void> markAsRead(UUID notificationId, UUID userId);

    Mono<Void> markAllAsRead(UUID userId);

    Mono<Void> deleteNotification(UUID notificationId, UUID userId);

    Mono<NotificationPreference> getPreferences(UUID userId);

    Mono<NotificationPreference> updatePreferences(UUID userId, NotificationPreference newPrefs);
}
