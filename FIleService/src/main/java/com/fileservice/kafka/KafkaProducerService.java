package com.fileservice.kafka;

import com.fileservice.event.FileEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "file.events";

    public void sendEvent(FileEvent event) {
        log.debug("Sending kafka event: {}", event);
        try {
            kafkaTemplate.send(TOPIC, event.getFileId().toString(), event);
        } catch (Exception e) {
            log.error("Failed to send kafka event", e);
            // In a strict transactional system, if kafka fails AFTER db commit,
            // we might be in an inconsistent state regarding downstream systems.
            // Using TransactionalEventListener + Kafka transaction manager mitigates this
            // by committing the kafka write only if it succeeds.
            // But here we are just sending.
        }
    }
}
