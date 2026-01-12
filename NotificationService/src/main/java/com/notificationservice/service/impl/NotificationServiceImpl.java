package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.model.domain.NotificationPreference;
import com.notificationservice.repository.NotificationPreferenceRepository;
import com.notificationservice.repository.NotificationRepository;
import com.notificationservice.service.DeliveryService;
import com.notificationservice.service.NotificationService;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final DeliveryService deliveryService;

    @Override
    public Mono<Void> sendNotification(UUID userId, String type, String title, String message, String priority,
            Map<String, String> data, List<String> channels) {

        Notification notification = Notification.builder()
                .userId(userId)
                .notificationType(type)
                .title(title)
                .message(message)
                .priority(priority)
                .data(data != null ? Json.of(serializeMap(data)) : null)
                .read(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(90))
                .build();

        return notificationRepository.save(notification)
                .flatMap(saved -> deliveryService.deliver(saved, channels))
                .onErrorResume(e -> {
                    log.error("Failed to process notification for user {}: {}", userId, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Flux<Notification> getNotifications(UUID userId, boolean unreadOnly, String type, int limit, int offset) {
        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (unreadOnly) {
            return notificationRepository.findByUserIdAndRead(userId, false, pageRequest);
        } else if (type != null && !type.isEmpty()) {
            return notificationRepository.findByUserIdAndNotificationType(userId, type, pageRequest);
        } else {
            return notificationRepository.findByUserId(userId, pageRequest);
        }
    }

    @Override
    public Mono<Long> getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    @Override
    public Mono<Void> markAsRead(UUID notificationId, UUID userId) {
        return notificationRepository.markAsRead(notificationId, userId);
    }

    @Override
    public Mono<Void> markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }

    @Override
    public Mono<Void> deleteNotification(UUID notificationId, UUID userId) {
        return notificationRepository.deleteByIdAndUserId(notificationId, userId);
    }

    @Override
    public Mono<NotificationPreference> getPreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId)
                .switchIfEmpty(createDefaultPreferences(userId));
    }

    @Override
    public Mono<NotificationPreference> updatePreferences(UUID userId, NotificationPreference newPrefs) {
        return preferenceRepository.findByUserId(userId)
                .flatMap(existing -> {
                    // Update fields
                    existing.setEmailEnabled(newPrefs.isEmailEnabled());
                    existing.setPushEnabled(newPrefs.isPushEnabled());
                    existing.setWebsocketEnabled(newPrefs.isWebsocketEnabled());
                    existing.setFileNotifications(newPrefs.isFileNotifications());
                    existing.setSyncNotifications(newPrefs.isSyncNotifications());
                    existing.setShareNotifications(newPrefs.isShareNotifications());
                    existing.setAdminNotifications(newPrefs.isAdminNotifications());
                    existing.setSystemNotifications(newPrefs.isSystemNotifications());
                    existing.setQuietHoursEnabled(newPrefs.isQuietHoursEnabled());
                    existing.setQuietHoursStart(newPrefs.getQuietHoursStart());
                    existing.setQuietHoursEnd(newPrefs.getQuietHoursEnd());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return preferenceRepository.save(existing);
                })
                .switchIfEmpty(preferenceRepository.save(newPrefs));
    }

    private Mono<NotificationPreference> createDefaultPreferences(UUID userId) {
        NotificationPreference defaultPrefs = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .pushEnabled(true)
                .websocketEnabled(true)
                .fileNotifications(true)
                .syncNotifications(true)
                .shareNotifications(true)
                .adminNotifications(true)
                .systemNotifications(true)
                .quietHoursEnabled(false)
                .updatedAt(LocalDateTime.now())
                .build();
        return preferenceRepository.save(defaultPrefs);
    }

    private String serializeMap(Map<String, String> data) {
        // Simple serialization or use a real JSON library
        StringBuilder sb = new StringBuilder("{");
        data.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.length() > 1)
            sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
}
