package com.authservice.service.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendUserRegisteredEvent(UUID userId, String email, String name) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_REGISTERED");
        event.put("userId", userId.toString());
        event.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("email", email);
        metadata.put("name", name);
        event.put("metadata", metadata);

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", userId.toString(), message);
            log.info("Sent USER_REGISTERED event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_REGISTERED event", e);
        }
    }

    public void sendUserBlockedEvent(UUID userId, String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_BLOCKED");
        event.put("userId", userId.toString());
        event.put("timestamp", java.time.LocalDateTime.now().toString());

        Map<String, String> metadata = new HashMap<>();
        metadata.put("reason", reason);
        event.put("metadata", metadata);

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", userId.toString(), message);
            log.info("Sent USER_BLOCKED event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_BLOCKED event", e);
        }
    }

    public void sendUserUnblockedEvent(UUID userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_UNBLOCKED");
        event.put("userId", userId.toString());
        event.put("timestamp", java.time.LocalDateTime.now().toString());
        // No metadata needed

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", userId.toString(), message);
            log.info("Sent USER_UNBLOCKED event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_UNBLOCKED event", e);
        }
    }

    public void sendUserDeletedEvent(UUID userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_DELETED");
        event.put("userId", userId.toString());
        event.put("timestamp", java.time.LocalDateTime.now().toString());

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", userId.toString(), message);
            log.info("Sent USER_DELETED event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_DELETED event", e);
        }
    }

    public void sendUserLoggedInEvent(UUID userId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USER_LOGGED_IN");
        event.put("userId", userId.toString());
        event.put("timestamp", java.time.LocalDateTime.now().toString());

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-events", userId.toString(), message);
            log.info("Sent USER_LOGGED_IN event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_LOGGED_IN event", e);
        }
    }
}
