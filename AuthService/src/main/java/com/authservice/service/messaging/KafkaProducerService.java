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
        event.put("email", email);
        event.put("name", name);
        event.put("timestamp", System.currentTimeMillis());

        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user.events", userId.toString(), message);
            log.info("Sent USER_REGISTERED event for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send USER_REGISTERED event", e);
        }
    }
}








