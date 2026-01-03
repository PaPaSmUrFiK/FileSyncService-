package com.filesync.userservice.service.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendUserEvent(String eventType, String userId, String payload) {
        // Ideally use a structured JSON or Avro object
        // payload implies serialized JSON
        String message = String.format("{\"eventType\":\"%s\", \"userId\":\"%s\", \"payload\":%s}", eventType, userId,
                payload);
        kafkaTemplate.send("user.events", userId, message);
        log.info("Sent user.event: {}", message);
    }

    public void sendAdminEvent(String eventType, String adminId, String payload) {
        String message = String.format("{\"eventType\":\"%s\", \"adminId\":\"%s\", \"payload\":%s}", eventType, adminId,
                payload);
        kafkaTemplate.send("admin.events", adminId, message);
        log.info("Sent admin.event: {}", message);
    }
}
