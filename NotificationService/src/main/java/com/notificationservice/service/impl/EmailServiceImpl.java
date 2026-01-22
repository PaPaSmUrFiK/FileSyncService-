package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Override
    public Mono<Void> sendEmail(UUID userId, Notification notification) {
        return Mono.fromRunnable(() -> {
            log.info("Email service disabled (Mailpit removed). Would send email to user {} with title: {}", userId,
                    notification.getTitle());
        });
    }

    @Override
    public Mono<Void> sendWelcomeEmail(UUID userId, String email) {
        return Mono.fromRunnable(() -> {
            log.info("Email service disabled (Mailpit removed). Would send welcome email to: {}", email);
        });
    }
}
