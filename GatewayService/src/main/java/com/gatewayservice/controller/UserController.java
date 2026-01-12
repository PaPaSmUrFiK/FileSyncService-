package com.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public Mono<ResponseEntity<Object>> getUser(@RequestHeader("X-User-Id") String userId) {
        log.info("Get user request for userId: {}", userId);
        return userServiceClient.getUser(userId)
                .map(response -> {
                    try {
                        String json = convertUserResponseToJson(response);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) json);
                    } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Get user error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PutMapping
    public Mono<ResponseEntity<Object>> updateUser(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> requestBody) {
        log.info("Update user request for userId: {}", userId);
        String name = requestBody.containsKey("name") ? (String) requestBody.get("name") : null;
        String avatarUrl = requestBody.containsKey("avatarUrl") ? (String) requestBody.get("avatarUrl") : null;
        
        return userServiceClient.updateUser(userId, name, avatarUrl)
                .map(response -> {
                    try {
                        String json = convertUserResponseToJson(response);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) json);
                    } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Update user error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @DeleteMapping
    public Mono<ResponseEntity<Object>> deleteUser(@RequestHeader("X-User-Id") String userId) {
        log.info("Delete user request for userId: {}", userId);
        return userServiceClient.deleteUser(userId)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    log.error("Delete user error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/settings")
    public Mono<ResponseEntity<Object>> getUserSettings(@RequestHeader("X-User-Id") String userId) {
        log.info("Get user settings request for userId: {}", userId);
        return userServiceClient.getUserSettings(userId)
                .map(response -> {
                    try {
                        String json = convertSettingsResponseToJson(response);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) json);
                    } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Get user settings error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PutMapping("/settings")
    public Mono<ResponseEntity<Object>> updateUserSettings(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> requestBody) {
        log.info("Update user settings request for userId: {}", userId);
        
        String theme = requestBody.containsKey("theme") ? (String) requestBody.get("theme") : null;
        String language = requestBody.containsKey("language") ? (String) requestBody.get("language") : null;
        Boolean notificationsEnabled = requestBody.containsKey("notificationsEnabled") 
                ? (Boolean) requestBody.get("notificationsEnabled") : null;
        Boolean emailNotifications = requestBody.containsKey("emailNotifications") 
                ? (Boolean) requestBody.get("emailNotifications") : null;
        Boolean autoSync = requestBody.containsKey("autoSync") 
                ? (Boolean) requestBody.get("autoSync") : null;
        Boolean syncOnMobileData = requestBody.containsKey("syncOnMobileData") 
                ? (Boolean) requestBody.get("syncOnMobileData") : null;
        
        return userServiceClient.updateUserSettings(userId, theme, language, notificationsEnabled,
                emailNotifications, autoSync, syncOnMobileData)
                .map(response -> {
                    try {
                        String json = convertSettingsResponseToJson(response);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) json);
                    } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Update user settings error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/quota")
    public Mono<ResponseEntity<Object>> checkQuota(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false, defaultValue = "0") long fileSize) {
        log.info("Check quota request for userId: {}, fileSize: {}", userId, fileSize);
        return userServiceClient.checkQuota(userId, fileSize)
                .map(response -> {
                    try {
                        String json = convertQuotaResponseToJson(response);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body((Object) json);
                    } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        throw new RuntimeException(e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Check quota error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    private String convertUserResponseToJson(com.filesync.user.grpc.UserResponse response) throws Exception {
        Map<String, Object> json = Map.of(
                "id", response.getId(),
                "email", response.getEmail(),
                "name", response.getName(),
                "avatarUrl", response.getAvatarUrl(),
                "roles", response.getRolesList(),
                "storageUsed", response.getStorageUsed(),
                "storageQuota", response.getStorageQuota(),
                "createdAt", response.getCreatedAt(),
                "updatedAt", response.getUpdatedAt()
        );
        return objectMapper.writeValueAsString(json);
    }

    private String convertSettingsResponseToJson(com.filesync.user.grpc.SettingsResponse response) throws Exception {
        Map<String, Object> json = Map.of(
                "userId", response.getUserId(),
                "theme", response.getTheme(),
                "language", response.getLanguage(),
                "notificationsEnabled", response.getNotificationsEnabled(),
                "emailNotifications", response.getEmailNotifications(),
                "autoSync", response.getAutoSync(),
                "syncOnMobileData", response.getSyncOnMobileData()
        );
        return objectMapper.writeValueAsString(json);
    }

    private String convertQuotaResponseToJson(com.filesync.user.grpc.QuotaResponse response) throws Exception {
        Map<String, Object> json = Map.of(
                "hasSpace", response.getHasSpace(),
                "availableSpace", response.getAvailableSpace(),
                "storageUsed", response.getStorageUsed(),
                "storageQuota", response.getStorageQuota()
        );
        return objectMapper.writeValueAsString(json);
    }
}

