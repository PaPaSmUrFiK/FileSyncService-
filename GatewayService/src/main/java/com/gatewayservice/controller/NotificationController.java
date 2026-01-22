package com.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.NotificationServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public Mono<ResponseEntity<Object>> getNotifications(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) String notificationType,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        log.info("Get notifications request for userId: {}", userId);
        return notificationServiceClient.getNotifications(userId, unreadOnly, notificationType, limit, offset)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("total", response.getTotal());
                    map.put("unreadCount", response.getUnreadCount());
                    map.put("notifications", response.getNotificationsList().stream().map(n -> {
                        Map<String, Object> nm = new java.util.HashMap<>();
                        nm.put("id", n.getId());
                        nm.put("notificationType", n.getNotificationType());
                        nm.put("title", n.getTitle());
                        nm.put("message", n.getMessage());
                        nm.put("priority", n.getPriority());
                        nm.put("isRead", n.getIsRead());
                        nm.put("data", n.getDataMap());
                        nm.put("createdAt", n.getCreatedAt());
                        nm.put("readAt", n.getReadAt());
                        return nm;
                    }).collect(java.util.stream.Collectors.toList()));
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Get notifications error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/unread-count")
    public Mono<ResponseEntity<Object>> getUnreadCount(@RequestHeader("X-User-Id") String userId) {
        log.info("Get unread count request for userId: {}", userId);
        return notificationServiceClient.getUnreadCount(userId)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("count", response.getCount());
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Get unread count error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PutMapping("/{notificationId}/read")
    public Mono<ResponseEntity<Object>> markAsRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String notificationId) {
        log.info("Mark as read request for notificationId: {}, userId: {}", notificationId, userId);
        return notificationServiceClient.markAsRead(notificationId, userId)
                .then(Mono.just(ResponseEntity.ok().build()))
                .onErrorResume(e -> {
                    log.error("Mark as read error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PutMapping("/read-all")
    public Mono<ResponseEntity<Object>> markAllAsRead(@RequestHeader("X-User-Id") String userId) {
        log.info("Mark all as read request for userId: {}", userId);
        return notificationServiceClient.markAllAsRead(userId)
                .then(Mono.just(ResponseEntity.ok().build()))
                .onErrorResume(e -> {
                    log.error("Mark all as read error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @DeleteMapping("/{notificationId}")
    public Mono<ResponseEntity<Object>> deleteNotification(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String notificationId) {
        log.info("Delete notification request for notificationId: {}, userId: {}", notificationId, userId);
        return notificationServiceClient.deleteNotification(notificationId, userId)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    log.error("Delete notification error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @DeleteMapping
    public Mono<ResponseEntity<Object>> deleteAllNotifications(@RequestHeader("X-User-Id") String userId) {
        log.info("Delete all notifications request for userId: {}", userId);
        return notificationServiceClient.deleteAllNotifications(userId)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    log.error("Delete all notifications error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PostMapping("/push-tokens")
    public Mono<ResponseEntity<Object>> registerPushToken(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> requestBody) {
        log.info("Register push token request for userId: {}", userId);

        String deviceId = requestBody.get("deviceId");
        String token = requestBody.get("token");
        String platform = requestBody.get("platform");

        return notificationServiceClient.registerPushToken(userId, deviceId, token, platform)
                .then(Mono.just(ResponseEntity.ok().build()))
                .onErrorResume(e -> {
                    log.error("Register push token error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @DeleteMapping("/push-tokens/{deviceId}")
    public Mono<ResponseEntity<Object>> unregisterPushToken(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String deviceId) {
        log.info("Unregister push token request for userId: {}, deviceId: {}", userId, deviceId);
        return notificationServiceClient.unregisterPushToken(userId, deviceId)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    log.error("Unregister push token error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/preferences")
    public Mono<ResponseEntity<Object>> getPreferences(@RequestHeader("X-User-Id") String userId) {
        log.info("Get preferences request for userId: {}", userId);
        return notificationServiceClient.getPreferences(userId)
                .map(response -> ResponseEntity.ok((Object) convertPreferencesToMap(response)))
                .onErrorResume(e -> {
                    log.error("Get preferences error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PutMapping("/preferences")
    public Mono<ResponseEntity<Object>> updatePreferences(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> requestBody) {
        log.info("Update preferences request for userId: {}", userId);

        Boolean emailEnabled = getBoolean(requestBody, "emailEnabled");
        Boolean pushEnabled = getBoolean(requestBody, "pushEnabled");
        Boolean websocketEnabled = getBoolean(requestBody, "websocketEnabled");
        Boolean fileNotifications = getBoolean(requestBody, "fileNotifications");
        Boolean syncNotifications = getBoolean(requestBody, "syncNotifications");
        Boolean shareNotifications = getBoolean(requestBody, "shareNotifications");
        Boolean adminNotifications = getBoolean(requestBody, "adminNotifications");
        Boolean systemNotifications = getBoolean(requestBody, "systemNotifications");
        Boolean quietHoursEnabled = getBoolean(requestBody, "quietHoursEnabled");
        String quietHoursStart = (String) requestBody.get("quietHoursStart");
        String quietHoursEnd = (String) requestBody.get("quietHoursEnd");

        return notificationServiceClient.updatePreferences(userId, emailEnabled, pushEnabled,
                websocketEnabled, fileNotifications, syncNotifications, shareNotifications,
                adminNotifications, systemNotifications, quietHoursEnabled, quietHoursStart, quietHoursEnd)
                .map(response -> ResponseEntity.ok((Object) convertPreferencesToMap(response)))
                .onErrorResume(e -> {
                    log.error("Update preferences error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    private Map<String, Object> convertPreferencesToMap(com.notificationservice.grpc.PreferencesResponse response) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("userId", response.getUserId());
        map.put("emailEnabled", response.getEmailEnabled());
        map.put("pushEnabled", response.getPushEnabled());
        map.put("websocketEnabled", response.getWebsocketEnabled());
        map.put("fileNotifications", response.getFileNotifications());
        map.put("syncNotifications", response.getSyncNotifications());
        map.put("shareNotifications", response.getShareNotifications());
        map.put("adminNotifications", response.getAdminNotifications());
        map.put("systemNotifications", response.getSystemNotifications());
        map.put("quietHoursEnabled", response.getQuietHoursEnabled());
        map.put("quietHoursStart", response.getQuietHoursStart());
        map.put("quietHoursEnd", response.getQuietHoursEnd());
        return map;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null)
            return null;
        if (value instanceof Boolean)
            return (Boolean) value;
        if (value instanceof String)
            return Boolean.parseBoolean((String) value);
        return null;
    }
}
