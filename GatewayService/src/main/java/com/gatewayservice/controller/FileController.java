package com.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.FileServiceClient;
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
@RequestMapping("/api/v1") // Изменяем базу для поддержки /files и /folders
@RequiredArgsConstructor
@Slf4j
public class FileController {

        private final FileServiceClient fileServiceClient;
        private final UserServiceClient userServiceClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @PostMapping("/files")
        public Mono<ResponseEntity<Object>> createFile(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Create file request for userId: {}", userId);

                String name = (String) requestBody.get("name");
                String path = (String) requestBody.get("path");
                Number sizeObj = (Number) requestBody.getOrDefault("size", 0);
                long size = sizeObj.longValue();
                String mimeType = (String) requestBody.getOrDefault("mimeType", "application/octet-stream");
                String hash = (String) requestBody.get("hash");
                Boolean isFolderObj = (Boolean) requestBody.getOrDefault("isFolder", false);
                boolean isFolder = isFolderObj != null && isFolderObj;
                String parentFolderId = (String) requestBody.get("parentFolderId");

                return fileServiceClient.createFile(userId, name, path, size, mimeType, hash, isFolder, parentFolderId)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Create file error: {}", e.getMessage());
                                        HttpStatus status = e.getMessage().contains("quota")
                                                        ? HttpStatus.FORBIDDEN
                                                        : HttpStatus.INTERNAL_SERVER_ERROR;
                                        return Mono.just(ResponseEntity.status(status)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/{fileId}")
        public Mono<ResponseEntity<Object>> getFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId) {
                log.info("Get file request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.getFile(fileId, userId)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Get file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PutMapping("/files/{fileId}")
        public Mono<ResponseEntity<Object>> updateFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Update file request for fileId: {}, userId: {}", fileId, userId);

                String name = (String) requestBody.get("name");
                Number sizeObj = (Number) requestBody.get("size");
                Long size = sizeObj != null ? sizeObj.longValue() : null;
                String hash = (String) requestBody.get("hash");
                Number versionObj = (Number) requestBody.get("version");
                Integer version = versionObj != null ? versionObj.intValue() : null;

                return fileServiceClient.updateFile(fileId, userId, name, size, hash, version)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Update file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/files/{fileId}")
        public Mono<ResponseEntity<Object>> deleteFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId) {
                log.info("Delete file request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.deleteFile(fileId, userId)
                                .then(Mono.just(ResponseEntity.noContent().build()))
                                .onErrorResume(e -> {
                                        log.error("Delete file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files")
        public Mono<ResponseEntity<Object>> listFiles(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestParam(required = false) String path,
                        @RequestParam(required = false) String parentFolderId,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false, defaultValue = "50") int limit,
                        @RequestParam(required = false, defaultValue = "0") int offset) {
                log.info("List files request for userId: {}", userId);
                return fileServiceClient.listFiles(userId, path, parentFolderId, search, limit, offset)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("files", response.getFilesList().stream()
                                                        .map(this::convertFileMetadataToMap)
                                                        .collect(java.util.stream.Collectors.toList()));
                                        result.put("total", response.getTotal());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("List files error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/trash")
        public Mono<ResponseEntity<Object>> listTrash(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestParam(required = false, defaultValue = "50") int limit,
                        @RequestParam(required = false, defaultValue = "0") int offset) {
                log.info("List trash request for userId: {}", userId);
                return fileServiceClient.listTrash(userId, limit, offset)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("files", response.getFilesList().stream()
                                                        .map(this::convertFileMetadataToMap)
                                                        .collect(java.util.stream.Collectors.toList()));
                                        result.put("total", response.getTotal());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("List trash error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/files/{fileId}/restore")
        public Mono<ResponseEntity<Object>> restoreFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId) {
                log.info("Restore file request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.restoreFile(fileId, userId)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Restore file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/files/trash/empty")
        public Mono<ResponseEntity<Object>> emptyTrash(
                        @RequestHeader("X-User-Id") String userId) {
                log.info("Empty trash request for userId: {}", userId);
                return fileServiceClient.emptyTrash(userId)
                                .thenReturn(ResponseEntity.accepted().build())
                                .onErrorResume(e -> {
                                        log.error("Empty trash error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/files/{fileId}/share")
        public Mono<ResponseEntity<Object>> shareFile(
                        @RequestHeader("X-User-Id") String ownerId,
                        @PathVariable String fileId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Share file request for fileId: {}, ownerId: {}", fileId, ownerId);

                String sharedWithUserIdInput = (String) requestBody.get("sharedWithUserId");
                String permission = (String) requestBody.getOrDefault("permission", "read");

                Mono<String> targetUserIdMono;
                if (sharedWithUserIdInput != null && sharedWithUserIdInput.contains("@")) {
                        log.info("Resolving email {} to userId", sharedWithUserIdInput);
                        targetUserIdMono = userServiceClient.getUserByEmail(sharedWithUserIdInput)
                                        .map(com.filesync.user.grpc.UserResponse::getId);
                } else {
                        targetUserIdMono = Mono.just(sharedWithUserIdInput);
                }

                return targetUserIdMono.flatMap(
                                targetUserId -> fileServiceClient.shareFile(fileId, ownerId, targetUserId, permission)
                                                .map(response -> {
                                                        Map<String, Object> result = new java.util.HashMap<>();
                                                        result.put("shareId", response.getShareId());
                                                        result.put("fileId", response.getFileId());
                                                        result.put("sharedWithUserId", response.getSharedWithUserId());
                                                        result.put("permission", response.getPermission());
                                                        result.put("createdAt", response.getCreatedAt());
                                                        return (ResponseEntity<Object>) ResponseEntity
                                                                        .ok((Object) result);
                                                }))
                                .onErrorResume(e -> {
                                        log.error("Share file error: {}", e.getMessage());
                                        HttpStatus status = e.getMessage().contains("not found") ? HttpStatus.NOT_FOUND
                                                        : HttpStatus.INTERNAL_SERVER_ERROR;
                                        return Mono.just(ResponseEntity.status(status)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/{fileId}/versions")
        public Mono<ResponseEntity<Object>> getFileVersions(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId) {
                log.info("Get file versions request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.getFileVersions(fileId, userId)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("versions", response.getVersionsList().stream().map(v -> {
                                                Map<String, Object> vm = new java.util.HashMap<>();
                                                vm.put("version", v.getVersion());
                                                vm.put("size", v.getSize());
                                                vm.put("hash", v.getHash());
                                                vm.put("createdAt", v.getCreatedAt());
                                                vm.put("createdBy", v.getCreatedByUserId());
                                                vm.put("createdByEmail", v.getCreatedByEmail());
                                                vm.put("createdByName", v.getCreatedByName());
                                                return vm;
                                        }).collect(java.util.stream.Collectors.toList()));
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("Get file versions error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/files/{fileId}/versions/{version}/restore")
        public Mono<ResponseEntity<Object>> restoreVersion(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @PathVariable int version) {
                log.info("Restore version request for fileId: {}, version: {}, userId: {}", fileId, version, userId);
                return fileServiceClient.restoreVersion(fileId, version, userId)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Restore version error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/{fileId}/permission")
        public Mono<ResponseEntity<Object>> checkPermission(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @RequestParam String requiredPermission) {
                log.info("Check permission request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.checkPermission(fileId, userId, requiredPermission)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("hasPermission", response.getHasPermission());
                                        result.put("permission", response.getPermission());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("Check permission error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PutMapping("/files/{fileId}/move")
        public Mono<ResponseEntity<Object>> moveFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Move file request for fileId: {}, userId: {}", fileId, userId);

                String newParentFolderId = (String) requestBody.get("newParentFolderId");

                return fileServiceClient.moveFile(fileId, newParentFolderId, userId)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Move file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        /*
         * =========================
         * SHARING ENDPOINTS (NEW)
         * =========================
         */

        @GetMapping("/files/shared-with-me")
        public Mono<ResponseEntity<Object>> listSharedWithMe(
                        @RequestHeader("X-User-Id") String userId) {
                log.info("List shared with me request for userId: {}", userId);
                return fileServiceClient.listSharedWithMe(userId)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        // Return as 'shares' to match frontend expectations
                                        result.put("shares", response.getFilesList().stream()
                                                        .map(fileMetadata -> {
                                                                Map<String, Object> share = new java.util.HashMap<>();
                                                                share.put("shareId", fileMetadata.getShareId() != null
                                                                                && !fileMetadata.getShareId().isEmpty()
                                                                                                ? fileMetadata.getShareId()
                                                                                                : fileMetadata.getId());
                                                                share.put("fileId", fileMetadata.getId());
                                                                share.put("fileName", fileMetadata.getName());
                                                                share.put("isFolder", fileMetadata.getIsFolder());
                                                                share.put("size", fileMetadata.getSize());
                                                                share.put("mimeType", fileMetadata.getMimeType());
                                                                share.put("permission", fileMetadata
                                                                                .getPermission() != null
                                                                                && !fileMetadata.getPermission()
                                                                                                .isEmpty() ? fileMetadata
                                                                                                                .getPermission()
                                                                                                                : "READ");
                                                                share.put("createdAt", fileMetadata.getCreatedAt());
                                                                share.put("ownerEmail", fileMetadata.getOwnerEmail());
                                                                share.put("ownerName", fileMetadata.getOwnerName());
                                                                return share;
                                                        })
                                                        .collect(java.util.stream.Collectors.toList()));
                                        result.put("total", response.getTotal());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("List shared with me error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/my-shares")
        public Mono<ResponseEntity<Object>> listAllMyShares(
                        @RequestHeader("X-User-Id") String ownerId,
                        @RequestParam(required = false) String fileId) {
                log.info("List all my shares request for ownerId: {}, fileId: {}", ownerId, fileId);
                // If fileId is provided, use it; otherwise use empty string to get all shares
                String targetFileId = (fileId != null && !fileId.isEmpty()) ? fileId : "";
                return fileServiceClient.listMyShares(ownerId, targetFileId)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("shares", response.getSharesList().stream().map(share -> {
                                                Map<String, Object> sm = new java.util.HashMap<>();
                                                sm.put("shareId", share.getShareId());
                                                sm.put("fileId", share.getFileId());
                                                sm.put("fileName", share.getFileName());
                                                sm.put("sharedWithUserId", share.getSharedWithUserId());
                                                sm.put("sharedWithEmail", share.getSharedWithEmail());
                                                sm.put("permission", share.getPermission());
                                                sm.put("createdAt", share.getCreatedAt());
                                                return sm;
                                        }).collect(java.util.stream.Collectors.toList()));
                                        result.put("total", response.getTotal());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("List all my shares error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/{fileId}/shares")
        public Mono<ResponseEntity<Object>> listMyShares(
                        @RequestHeader("X-User-Id") String ownerId,
                        @PathVariable String fileId) {
                log.info("List my shares request for fileId: {}, ownerId: {}", fileId, ownerId);
                return fileServiceClient.listMyShares(ownerId, fileId)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("shares", response.getSharesList().stream().map(share -> {
                                                Map<String, Object> sm = new java.util.HashMap<>();
                                                sm.put("shareId", share.getShareId());
                                                sm.put("fileId", share.getFileId());
                                                sm.put("fileName", share.getFileName());
                                                sm.put("sharedWithUserId", share.getSharedWithUserId());
                                                sm.put("sharedWithEmail", share.getSharedWithEmail());
                                                sm.put("permission", share.getPermission());
                                                sm.put("createdAt", share.getCreatedAt());
                                                return sm;
                                        }).collect(java.util.stream.Collectors.toList()));
                                        result.put("total", response.getTotal());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("List my shares error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/files/shares/{shareId}")
        public Mono<ResponseEntity<Object>> revokeShare(
                        @RequestHeader("X-User-Id") String ownerId,
                        @PathVariable String shareId) {
                log.info("Revoke share request for shareId: {}, ownerId: {}", shareId, ownerId);
                return fileServiceClient.revokeShare(shareId, ownerId)
                                .then(Mono.just(ResponseEntity.noContent().build()))
                                .onErrorResume(e -> {
                                        log.error("Revoke share error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/files/{fileId}/access-context")
        public Mono<ResponseEntity<Object>> getFileAccessContext(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId) {
                log.info("Get file access context request for fileId: {}, userId: {}", fileId, userId);
                return fileServiceClient.getFileAccessContext(fileId, userId)
                                .map(response -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("accessType", response.getAccessType().name());
                                        result.put("permission", response.getPermission());
                                        result.put("canRead", response.getCanRead());
                                        result.put("canWrite", response.getCanWrite());
                                        result.put("canDelete", response.getCanDelete());
                                        result.put("canShare", response.getCanShare());
                                        result.put("existingShares",
                                                        response.getExistingSharesList().stream().map(share -> {
                                                                Map<String, Object> sm = new java.util.HashMap<>();
                                                                sm.put("shareId", share.getShareId());
                                                                sm.put("sharedWithUserId", share.getSharedWithUserId());
                                                                sm.put("sharedWithEmail", share.getSharedWithEmail());
                                                                sm.put("permission", share.getPermission());
                                                                return sm;
                                                        }).collect(java.util.stream.Collectors.toList()));
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("Get file access context error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/files/{fileId}/versions")
        public Mono<ResponseEntity<Object>> addFileVersion(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Add file version request for fileId: {}, userId: {}", fileId, userId);

                Number sizeObj = (Number) requestBody.get("size");
                long size = sizeObj != null ? sizeObj.longValue() : 0;
                String hash = (String) requestBody.get("hash");

                return fileServiceClient.addFileVersion(fileId, userId, size, hash)
                                .map(version -> {
                                        Map<String, Object> result = new java.util.HashMap<>();
                                        result.put("version", version.getVersion());
                                        result.put("size", version.getSize());
                                        result.put("hash", version.getHash());
                                        result.put("storagePath", version.getStoragePath());
                                        result.put("createdAt", version.getCreatedAt());
                                        result.put("createdByUserId", version.getCreatedByUserId());
                                        return ResponseEntity.ok((Object) result);
                                })
                                .onErrorResume(e -> {
                                        log.error("Add file version error: {}", e.getMessage());
                                        HttpStatus status = e.getMessage().contains("permission")
                                                        ? HttpStatus.FORBIDDEN
                                                        : HttpStatus.INTERNAL_SERVER_ERROR;
                                        return Mono.just(ResponseEntity.status(status)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        /*
         * =========================
         * FOLDER ENDPOINTS
         * =========================
         */

        @PostMapping("/folders")
        public Mono<ResponseEntity<Object>> createFolder(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Create folder request for userId: {}", userId);

                String name = (String) requestBody.get("name");
                String path = (String) requestBody.get("path");
                String parentFolderId = (String) requestBody.get("parentFolderId");

                // Папка - это файл с флагом isFolder=true и нулевым размером
                return fileServiceClient.createFile(userId, name, path, 0, "inode/directory", "", true, parentFolderId)
                                .map(response -> ResponseEntity.ok((Object) convertFileMetadataToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Create folder error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @GetMapping("/folders/{folderId}")
        public Mono<ResponseEntity<Object>> getFolder(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String folderId) {
                return getFile(userId, folderId);
        }

        @PutMapping("/folders/{folderId}")
        public Mono<ResponseEntity<Object>> updateFolder(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String folderId,
                        @RequestBody Map<String, Object> requestBody) {
                return updateFile(userId, folderId, requestBody);
        }

        @DeleteMapping("/folders/{folderId}")
        public Mono<ResponseEntity<Object>> deleteFolder(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String folderId) {
                return deleteFile(userId, folderId);
        }

        private Map<String, Object> convertFileMetadataToMap(com.fileservice.grpc.FileMetadata response) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", response.getId());
                map.put("name", response.getName());
                map.put("path", response.getPath());
                map.put("size", response.getSize());
                map.put("mimeType", response.getMimeType());
                map.put("hash", response.getHash());
                map.put("isFolder", response.getIsFolder());
                map.put("version", response.getVersion());
                map.put("createdAt", response.getCreatedAt());
                map.put("updatedAt", response.getUpdatedAt());
                map.put("createdBy", response.getCreatedBy());
                map.put("uploadUrl", response.getUploadUrl());
                map.put("downloadUrl", response.getDownloadUrl());
                return map;
        }
}
