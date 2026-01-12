package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.repository.PushTokenRepository;
import com.notificationservice.service.PushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushServiceImpl implements PushService {

    private final PushTokenRepository tokenRepository;

    @Override
    public Mono<Void> sendPush(UUID userId, Notification notification) {
        return tokenRepository.findByUserIdAndActive(userId, true)
                .flatMap(token -> {
                    log.info("Sending push notification to device {} on platform {} with token {}",
                            token.getDeviceId(), token.getPlatform(), token.getToken());
                    // In real implementation, call FCM or APNS API here
                    return Mono.empty();
                })
                .then();
    }
}
