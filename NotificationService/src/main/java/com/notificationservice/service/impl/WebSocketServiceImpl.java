package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.service.WebSocketService;
import com.notificationservice.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final NotificationWebSocketHandler webSocketHandler;

    @Override
    public Mono<Void> sendNotification(UUID userId, Notification notification) {
        return Mono.fromRunnable(() -> {
            webSocketHandler.sendToUser(userId, Map.of(
                    "event", "notification",
                    "data", Map.of(
                            "id", notification.getId().toString(),
                            "type", notification.getNotificationType(),
                            "title", notification.getTitle(),
                            "message", notification.getMessage(),
                            "priority", notification.getPriority(),
                            "created_at", notification.getCreatedAt().toString())));
        });
    }

    @Override
    public Mono<Void> sendReadConfirmation(UUID userId, UUID notificationId) {
        return Mono.fromRunnable(() -> {
            webSocketHandler.sendToUser(userId, Map.of(
                    "event", "notification_read",
                    "data", Map.of("notification_id", notificationId.toString())));
        });
    }

    @Override
    public Mono<Void> sendAllReadConfirmation(UUID userId, int markedCount) {
        return Mono.fromRunnable(() -> {
            webSocketHandler.sendToUser(userId, Map.of(
                    "event", "all_notifications_read",
                    "data", Map.of("marked_count", markedCount)));
        });
    }
}
