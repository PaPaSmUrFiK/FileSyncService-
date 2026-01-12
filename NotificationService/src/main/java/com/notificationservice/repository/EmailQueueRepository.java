package com.notificationservice.repository;

import com.notificationservice.model.domain.EmailQueue;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface EmailQueueRepository extends ReactiveCrudRepository<EmailQueue, UUID> {
    Flux<EmailQueue> findByStatus(String status);
}
