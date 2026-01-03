package com.fileservice.service;

import com.fileservice.event.FileEvent;
import com.fileservice.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final KafkaProducerService kafkaProducerService;

    // Method to be called by business logic within transaction
    public void publish(FileEvent event) {
        log.debug("Publishing application event for: {}", event.getEventType());
        // Publish internal Spring event first
        applicationEventPublisher.publishEvent(event);
    }

    // Listener that runs ONLY after DB commit
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(FileEvent event) {
        log.info("Transaction committed, sending Kafka event: {}", event.getEventType());
        kafkaProducerService.sendEvent(event);
    }
}
