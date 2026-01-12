package com.notificationservice.service;

import com.notificationservice.model.domain.Notification;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PushService {
    Mono<Void> sendPush(UUID userId, Notification notification);
}
