package com.notificationservice.repository;

import com.notificationservice.model.domain.PushToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PushTokenRepository extends ReactiveCrudRepository<PushToken, UUID> {
    Flux<PushToken> findByUserIdAndActive(UUID userId, boolean active);

    Mono<Void> deleteByUserIdAndDeviceId(UUID userId, UUID deviceId);
}
