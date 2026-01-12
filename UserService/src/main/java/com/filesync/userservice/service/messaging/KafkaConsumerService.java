package com.filesync.userservice.service.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filesync.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final UserService userService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "file.events", groupId = "user-service-group")
    public void consumeFileEvent(String message) {
        log.info("Received file.event: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "";

            if ("FILE_UPLOADED".equals(eventType) || "FILE_DELETED".equals(eventType)) {
                if (node.has("userId") && node.has("sizeDelta")) {
                    UUID userId = UUID.fromString(node.get("userId").asText());
                    long delta = node.get("sizeDelta").asLong();
                    userService.updateStorageUsed(userId, delta);
                    log.info("Updated storage for user {} by {}", userId, delta);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process file.event", e);
        }
    }

    @KafkaListener(topics = "user.events", groupId = "user-service-group")
    public void consumeUserEvent(String message) {
        log.info("Received user.event: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "";

            if ("USER_REGISTERED".equals(eventType)) {
                UUID userId = UUID.fromString(node.get("userId").asText());
                String email = node.get("email").asText();
                String name = node.has("name") ? node.get("name").asText() : "";
                userService.createUser(userId, email, name);
            }
        } catch (Exception e) {
            log.error("Failed to process user.event", e);
        }
    }

    @KafkaListener(topics = "sync.events", groupId = "user-service-group")
    public void consumeSyncEvent(String message) {
        log.info("Received sync.event: {}", message);
        // Update last_active_at logic here if we had that field in User entity (User
        // entity has updatedAt but not lastActiveAt specifically, although
        // Auth/Protocol has it).
        // Since User table doesn't have explicit last_activity (it relies on Auth for
        // login time), we might update 'updated_at' or a new field.
        // Skipping implementation to avoid schema changes not in prompt.
    }
}
