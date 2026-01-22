package com.notificationservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.model.domain.Notification;
import com.notificationservice.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC = "sync-notifications";

    @Override
    public Mono<Void> sendNotification(UUID userId, Notification notification) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId.toString());
                payload.put("notificationId", notification.getId().toString());
                payload.put("type", notification.getNotificationType());
                payload.put("title", notification.getTitle());
                payload.put("message", notification.getMessage());
                payload.put("priority", notification.getPriority());
                if (notification.getResourceId() != null) {
                    payload.put("resourceId", notification.getResourceId().toString());
                }
                payload.put("resourceType", notification.getResourceType());
                payload.put("createdAt", notification.getCreatedAt().toString());

                String json = objectMapper.writeValueAsString(payload);
                kafkaTemplate.send(TOPIC, userId.toString(), json)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to send notification to SyncService via Kafka: {}", ex.getMessage());
                            } else {
                                log.debug("Notification sent to SyncService: {}", notification.getId());
                            }
                        });
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize notification for SyncService: {}", e.getMessage());
            }
        });
    }

    @Override
    public Mono<Void> sendReadConfirmation(UUID userId, UUID notificationId) {
        return sendEvent(userId, notificationId, "NOTIFICATION_READ", null);
    }

    @Override
    public Mono<Void> sendAllReadConfirmation(UUID userId, int markedCount) {
        return sendEvent(userId, null, "ALL_NOTIFICATIONS_READ", Map.of("count", markedCount));
    }

    @Override
    public Mono<Void> sendDeleteConfirmation(UUID userId, UUID notificationId) {
        return sendEvent(userId, notificationId, "NOTIFICATION_DELETED", null);
    }

    @Override
    public Mono<Void> sendAllDeleteConfirmation(UUID userId) {
        return sendEvent(userId, null, "ALL_NOTIFICATIONS_DELETED", null);
    }

    private Mono<Void> sendEvent(UUID userId, UUID notificationId, String type, Object data) {
        return Mono.fromRunnable(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("userId", userId.toString());
                if (notificationId != null) {
                    payload.put("notificationId", notificationId.toString());
                }
                payload.put("type", type);
                payload.put("createdAt", java.time.LocalDateTime.now().toString());
                if (data != null) {
                    payload.put("data", data);
                }

                String json = objectMapper.writeValueAsString(payload);
                kafkaTemplate.send(TOPIC, userId.toString(), json)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to send event {} to SyncService via Kafka: {}", type,
                                        ex.getMessage());
                            } else {
                                log.debug("Event {} sent to SyncService", type);
                            }
                        });
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event for SyncService: {}", e.getMessage());
            }
        });
    }
}
