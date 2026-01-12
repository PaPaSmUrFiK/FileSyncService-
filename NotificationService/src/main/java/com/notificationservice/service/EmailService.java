package com.notificationservice.service;

import com.notificationservice.model.domain.Notification;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EmailService {
    Mono<Void> sendEmail(UUID userId, Notification notification);

    Mono<Void> sendWelcomeEmail(UUID userId, String email);
}
