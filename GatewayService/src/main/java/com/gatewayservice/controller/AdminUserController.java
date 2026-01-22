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

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminUserController {

        private final UserServiceClient userServiceClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        // Управление пользователями
        @GetMapping("/users")
        public Mono<ResponseEntity<Object>> listUsers(
                        @RequestHeader("X-User-Id") String adminId,
                        @RequestParam(required = false, defaultValue = "1") int page,
                        @RequestParam(required = false, defaultValue = "20") int pageSize,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String plan,
                        @RequestParam(required = false, defaultValue = "created_at") String sortBy,
                        @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
                log.info("List users request by admin: {}, page: {}, size: {}", adminId, page, pageSize);
                return userServiceClient.listUsers(adminId, page, pageSize, search, plan, sortBy, sortOrder)
                                .doOnNext(response -> log.info("Received response from UserService: {}",
                                                response.getClass().getSimpleName()))
                                .timeout(java.time.Duration.ofSeconds(10))
                                .flatMap(response -> convertToJsonResponseFixed(response))
                                .doOnSuccess(result -> log.info("Successfully converted response"))
                                .onErrorResume(e -> {
                                        log.error("List users error: {} - {}", e.getClass().getSimpleName(),
                                                        e.getMessage(), e);
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/users/{userId}")
        public Mono<ResponseEntity<Object>> getUserDetails(
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId) {
                log.info("Get user details request by admin: {} for user: {}", adminId, userId);
                return userServiceClient.getUserDetails(adminId, userId)
                                .flatMap(response -> convertToJsonResponse(response))
                                .onErrorResume(e -> {
                                        log.error("Get user details error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PutMapping("/users/{userId}/quota")
        public Mono<ResponseEntity<Object>> updateUserQuota(
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Update user quota request by admin: {} for user: {}", adminId, userId);
                long newQuota = ((Number) requestBody.get("newQuota")).longValue();
                return userServiceClient.updateUserQuota(adminId, userId, newQuota)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Update user quota error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PutMapping("/users/{userId}/plan")
        public Mono<ResponseEntity<Object>> changeUserPlan(
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Change user plan request by admin: {} for user: {}", adminId, userId);
                String newPlan = (String) requestBody.get("newPlan");
                return userServiceClient.changeUserPlan(adminId, userId, newPlan)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Change user plan error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/users/{userId}/block")
        public Mono<ResponseEntity<Object>> blockUser(
                        @RequestHeader(name = "Authorization", required = false) String token,
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Block user request by admin: {} for user: {}", adminId, userId);
                String reason = (String) requestBody.get("reason");
                return userServiceClient.blockUser(adminId, userId, reason, token)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Block user error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/users/{userId}/unblock")
        public Mono<ResponseEntity<Object>> unblockUser(
                        @RequestHeader(name = "Authorization", required = false) String token,
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId) {
                log.info("Unblock user request by admin: {} for user: {}", adminId, userId);
                return userServiceClient.unblockUser(adminId, userId, token)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Unblock user error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/users/{userId}/roles")
        public Mono<ResponseEntity<Object>> assignUserRole(
                        @RequestHeader(name = "Authorization", required = false) String token,
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Assign user role request by admin: {} for user: {}", adminId, userId);
                String roleName = (String) requestBody.get("roleName");
                return userServiceClient.assignUserRole(adminId, userId, roleName, token)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Assign user role error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/users/{userId}/roles")
        public Mono<ResponseEntity<Object>> revokeUserRole(
                        @RequestHeader(name = "Authorization", required = false) String token,
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId,
                        @RequestParam String roleName) {
                log.info("Revoke user role request by admin: {} for user: {}", adminId, userId);
                return userServiceClient.revokeUserRole(adminId, userId, roleName, token)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Revoke user role error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/users/{userId}")
        public Mono<ResponseEntity<Object>> deleteUser(
                        @RequestHeader("X-User-Id") String adminId,
                        @PathVariable String userId) {
                log.info("Delete user request by admin: {} for user: {}", adminId, userId);
                return userServiceClient.deleteUser(adminId, userId)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Delete user error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        // Статистика
        @GetMapping("/statistics/system")
        public Mono<ResponseEntity<Object>> getSystemStatistics(
                        @RequestHeader("X-User-Id") String adminId,
                        @RequestParam(required = false) Long fromTimestamp,
                        @RequestParam(required = false) Long toTimestamp) {
                log.info("Get system statistics request by admin: {}", adminId);
                return userServiceClient.getSystemStatistics(adminId, fromTimestamp, toTimestamp)
                                .flatMap(response -> convertToJsonResponse(response))
                                .onErrorResume(e -> {
                                        log.error("Get system statistics error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/statistics/storage")
        public Mono<ResponseEntity<Object>> getStorageStatistics(
                        @RequestHeader("X-User-Id") String adminId) {
                log.info("Get storage statistics request by admin: {}", adminId);
                return userServiceClient.getStorageStatistics(adminId)
                                .flatMap(response -> convertToJsonResponse(response))
                                .onErrorResume(e -> {
                                        log.error("Get storage statistics error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/statistics/users")
        public Mono<ResponseEntity<Object>> getUserStatistics(
                        @RequestHeader("X-User-Id") String adminId) {
                log.info("Get user statistics request by admin: {}", adminId);
                return userServiceClient.getUserStatistics(adminId)
                                .flatMap(response -> convertToJsonResponse(response))
                                .onErrorResume(e -> {
                                        log.error("Get user statistics error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/statistics/active")
        public Mono<ResponseEntity<Object>> getActiveUsers(
                        @RequestHeader("X-User-Id") String adminId,
                        @RequestParam(required = false, defaultValue = "60") int minutes) {
                log.info("Get active users request by admin: {}", adminId);
                return userServiceClient.getActiveUsers(adminId, minutes)
                                .flatMap(response -> convertToJsonResponse(response))
                                .onErrorResume(e -> {
                                        log.error("Get active users error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        /**
         * Fixed version that properly handles empty repeated fields (like roles)
         */
        private Mono<ResponseEntity<Object>> convertToJsonResponseFixed(Object response) {
                try {
                        Object jsonObject;
                        if (response instanceof MessageOrBuilder) {
                                // Convert Protobuf to JSON string
                                String protoJson = JsonFormat.printer()
                                                .omittingInsignificantWhitespace()
                                                .includingDefaultValueFields()
                                                .print((MessageOrBuilder) response);
                                log.debug("Protobuf JSON: {}", protoJson);
                                // Parse to Map to ensure proper handling
                                jsonObject = objectMapper.readValue(protoJson, Object.class);
                        } else {
                                jsonObject = response;
                        }
                        // Return the object directly, not as a JSON string
                        log.debug("Returning JSON object: {}", jsonObject);
                        return Mono.just(ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(jsonObject));
                } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                }
        }

        private Mono<ResponseEntity<Object>> convertToJsonResponse(Object response) {
                try {
                        String json;
                        if (response instanceof MessageOrBuilder) {
                                json = JsonFormat.printer()
                                                .omittingInsignificantWhitespace()
                                                .includingDefaultValueFields()
                                                .print((MessageOrBuilder) response);
                        } else {
                                json = objectMapper.writeValueAsString(response);
                        }
                        return Mono.just(ResponseEntity.ok()
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body((Object) json));
                } catch (Exception e) {
                        log.error("Error converting response to JSON", e);
                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                }
        }
}
