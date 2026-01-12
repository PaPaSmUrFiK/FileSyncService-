package com.notificationservice.repository;

import com.notificationservice.model.domain.NotificationPreference;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends ReactiveCrudRepository<NotificationPreference, UUID> {
    Mono<NotificationPreference> findByUserId(UUID userId);
}
