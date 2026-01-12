package com.notificationservice.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<UUID, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String userIdStr = (String) session.getAttributes().getOrDefault("userId", "");
        if (userIdStr.isEmpty()) {
            // Try extracting from path if not in attributes
            String path = session.getHandshakeInfo().getUri().getPath();
            userIdStr = path.substring(path.lastIndexOf('/') + 1);
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in WebSocket path: {}", userIdStr);
            return session.close();
        }

        Sinks.Many<String> sink = userSinks.computeIfAbsent(userId, k -> Sinks.many().multicast().directBestEffort());

        Mono<Void> output = session.send(sink.asFlux().map(session::textMessage));

        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(msg -> handleMessage(userId, msg))
                .doOnError(e -> log.error("WebSocket error for user {}: {}", userId, e.getMessage()))
                .doOnTerminate(() -> {
                    // Cleanup if no more sessions for this user?
                    // Multicast sink handles multiple sessions.
                })
                .then();

        return Mono.zip(input, output).then();
    }

    private void handleMessage(UUID userId, String message) {
        log.debug("Received message from user {}: {}", userId, message);
        // Handle ping/pong, mark_read, etc.
        try {
            Map<String, Object> map = objectMapper.readValue(message, Map.class);
            String action = (String) map.get("action");
            if ("ping".equals(action)) {
                sendToUser(userId, Map.of("event", "pong", "timestamp", java.time.Instant.now().toString()));
            }
            // Other actions could be delegated to a service
        } catch (JsonProcessingException e) {
            log.error("Failed to parse WebSocket message: {}", message);
        }
    }

    public void sendToUser(UUID userId, Object payload) {
        Sinks.Many<String> sink = userSinks.get(userId);
        if (sink != null) {
            try {
                sink.tryEmitNext(objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize WebSocket payload: {}", payload);
            }
        }
    }
}
