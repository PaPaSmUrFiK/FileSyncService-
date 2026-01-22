package com.notificationservice.service;

import com.notificationservice.model.domain.Notification;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface WebSocketService {
    Mono<Void> sendNotification(UUID userId, Notification notification);

    Mono<Void> sendReadConfirmation(UUID userId, UUID notificationId);

    Mono<Void> sendAllReadConfirmation(UUID userId, int markedCount);

    Mono<Void> sendDeleteConfirmation(UUID userId, UUID notificationId);

    Mono<Void> sendAllDeleteConfirmation(UUID userId);
}
