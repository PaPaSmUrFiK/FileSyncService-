package com.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.SyncServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final SyncServiceClient syncServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/devices")
    public Mono<ResponseEntity<Object>> registerDevice(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> requestBody) {
        log.info("Register device request for userId: {}", userId);
        
        String deviceName = requestBody.get("deviceName");
        String deviceType = requestBody.get("deviceType");
        String os = requestBody.get("os");
        String osVersion = requestBody.get("osVersion");
        
        return syncServiceClient.registerDevice(userId, deviceName, deviceType, os, osVersion)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("deviceId", response.getDeviceId());
                    map.put("syncToken", response.getSyncToken());
                    map.put("registeredAt", response.getRegisteredAt());
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Register device error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<Object>> getSyncStatus(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("device_id") String deviceId) {
        log.info("Get sync status request for deviceId: {}, userId: {}", deviceId, userId);
        return syncServiceClient.getSyncStatus(deviceId, userId)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("deviceId", response.getDeviceId());
                    map.put("status", response.getStatus());
                    map.put("lastSync", response.getLastSync());
                    map.put("pendingChanges", response.getPendingChanges());
                    map.put("syncedFiles", response.getSyncedFiles());
                    map.put("syncCursor", response.getSyncCursor());
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Get sync status error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PostMapping("/push")
    public Mono<ResponseEntity<Object>> pushChanges(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> requestBody) {
        String deviceId = (String) requestBody.get("deviceId");
        log.info("Push changes request for deviceId: {}, userId: {}", deviceId, userId);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changesList = (List<Map<String, Object>>) requestBody.get("changes");
        List<SyncServiceClient.FileChange> changes = new ArrayList<>();
        
        if (changesList != null) {
            for (Map<String, Object> changeMap : changesList) {
                SyncServiceClient.FileChange change = new SyncServiceClient.FileChange();
                change.fileId = (String) changeMap.get("fileId");
                change.filePath = (String) changeMap.get("filePath");
                change.changeType = (String) changeMap.get("changeType");
                change.fileHash = (String) changeMap.get("fileHash");
                Number fileSizeObj = (Number) changeMap.get("fileSize");
                change.fileSize = fileSizeObj != null ? fileSizeObj.longValue() : 0;
                Number localVersionObj = (Number) changeMap.get("localVersion");
                change.localVersion = localVersionObj != null ? localVersionObj.intValue() : 1;
                change.clientTimestamp = (String) changeMap.get("clientTimestamp");
                change.oldPath = (String) changeMap.get("oldPath");
                changes.add(change);
            }
        }
        
        return syncServiceClient.pushChanges(deviceId, userId, changes)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("syncCursor", response.getSyncCursor());
                    map.put("results", response.getResultsList().stream().map(r -> {
                        Map<String, Object> rm = new java.util.HashMap<>();
                        rm.put("fileId", r.getFileId());
                        rm.put("status", r.getStatus());
                        rm.put("uploadUrl", r.getUploadUrl());
                        rm.put("serverVersion", r.getServerVersion());
                        rm.put("conflictId", r.getConflictId());
                        rm.put("errorMessage", r.getErrorMessage());
                        return rm;
                    }).collect(java.util.stream.Collectors.toList()));
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Push changes error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/pull")
    public Mono<ResponseEntity<Object>> pullChanges(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("device_id") String deviceId,
            @RequestParam(value = "last_sync_cursor", required = false) String lastSyncCursor) {
        log.info("Pull changes request for deviceId: {}, userId: {}", deviceId, userId);
        
        return syncServiceClient.pullChanges(deviceId, userId, lastSyncCursor)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("syncCursor", response.getSyncCursor());
                    map.put("changes", response.getChangesList().stream().map(c -> {
                        Map<String, Object> cm = new java.util.HashMap<>();
                        cm.put("fileId", c.getFileId());
                        cm.put("filePath", c.getFilePath());
                        cm.put("changeType", c.getChangeType());
                        cm.put("fileHash", c.getFileHash());
                        cm.put("fileSize", c.getFileSize());
                        cm.put("serverVersion", c.getVersion());
                        cm.put("timestamp", c.getTimestamp());
                        return cm;
                    }).collect(java.util.stream.Collectors.toList()));
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Pull changes error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @PostMapping("/conflicts/{conflictId}/resolve")
    public Mono<ResponseEntity<Object>> resolveConflict(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String conflictId,
            @RequestBody Map<String, String> requestBody) {
        log.info("Resolve conflict request for conflictId: {}, userId: {}", conflictId, userId);
        
        String resolutionType = requestBody.get("resolutionType");
        String chosenFileId = requestBody.get("chosenFileId");
        
        return syncServiceClient.resolveConflict(conflictId, userId, resolutionType, chosenFileId)
                .then(Mono.just(ResponseEntity.ok().build()))
                .onErrorResume(e -> {
                    log.error("Resolve conflict error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @DeleteMapping("/devices/{deviceId}")
    public Mono<ResponseEntity<Object>> unregisterDevice(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String deviceId) {
        log.info("Unregister device request for deviceId: {}, userId: {}", deviceId, userId);
        return syncServiceClient.unregisterDevice(deviceId, userId)
                .then(Mono.just(ResponseEntity.noContent().build()))
                .onErrorResume(e -> {
                    log.error("Unregister device error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    @GetMapping("/devices")
    public Mono<ResponseEntity<Object>> getDevices(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Get devices request for userId: {}", userId);
        return syncServiceClient.getDevices(userId)
                .map(response -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("devices", response.getDevicesList().stream().map(d -> {
                        Map<String, Object> dm = new java.util.HashMap<>();
                        dm.put("deviceId", d.getDeviceId());
                        dm.put("deviceName", d.getDeviceName());
                        dm.put("deviceType", d.getDeviceType());
                        dm.put("os", d.getOs());
                        dm.put("lastSync", d.getLastSync());
                        dm.put("isOnline", d.getIsOnline());
                        return dm;
                    }).collect(java.util.stream.Collectors.toList()));
                    return ResponseEntity.ok((Object) map);
                })
                .onErrorResume(e -> {
                    log.error("Get devices error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new com.gatewayservice.model.ErrorResponse(e.getMessage())));
                });
    }

    // Удаляем неиспользуемые методы конвертации

}

