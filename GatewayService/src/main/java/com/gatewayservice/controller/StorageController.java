package com.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewayservice.client.StorageServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Slf4j
public class StorageController {

        private final StorageServiceClient storageServiceClient;
        private final com.gatewayservice.client.FileServiceClient fileServiceClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @PostMapping("/upload-url")
        public Mono<ResponseEntity<Object>> getUploadUrl(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, Object> requestBody) {
                String fileId = (String) requestBody.get("fileId");
                String fileName = (String) requestBody.get("fileName");
                Number fileSizeObj = (Number) requestBody.get("fileSize");
                long fileSize = fileSizeObj != null ? fileSizeObj.longValue() : 0;
                String mimeType = (String) requestBody.getOrDefault("mimeType", "application/octet-stream");
                Number versionObj = (Number) requestBody.getOrDefault("version", 1);
                int version = versionObj.intValue();

                log.info("Get upload URL request processing for fileId: {}, userId: {}", fileId, userId);
                return storageServiceClient.getUploadUrl(fileId, fileName, fileSize, mimeType, version)
                                .map(response -> {
                                        Map<String, Object> body = convertUrlResponseToMap(response);
                                        log.info("Returning upload URL response: {}", body);
                                        return ResponseEntity.ok((Object) body);
                                })
                                .onErrorResume(e -> {
                                        log.error("Get upload URL error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/download-url")
        public Mono<ResponseEntity<Object>> getDownloadUrl(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, Object> requestBody) {
                String fileId = (String) requestBody.get("fileId");
                Number versionObj = (Number) requestBody.get("version");
                Integer version = versionObj != null ? versionObj.intValue() : null;

                log.info("Get download URL request for fileId: {}, userId: {}", fileId, userId);

                return fileServiceClient.getFile(fileId, userId)
                                .flatMap(fileMetadata -> {
                                        String fileName = fileMetadata.getName();
                                        return storageServiceClient.getDownloadUrl(fileId, version, fileName);
                                })
                                .map(response -> ResponseEntity.ok((Object) convertUrlResponseToMap(response)))
                                .onErrorResume(e -> {
                                        log.error("Get download URL error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @DeleteMapping("/files/{fileId}")
        public Mono<ResponseEntity<Object>> deleteFile(
                        @RequestHeader("X-User-Id") String userId,
                        @PathVariable String fileId,
                        @RequestParam(required = false) Integer version) {
                log.info("Delete file from storage request for fileId: {}, userId: {}", fileId, userId);
                return storageServiceClient.deleteFile(fileId, version)
                                .then(Mono.just(ResponseEntity.noContent().build()))
                                .onErrorResume(e -> {
                                        log.error("Delete file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/files/copy")
        public Mono<ResponseEntity<Object>> copyFile(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, String> requestBody) {
                log.info("Copy file request, userId: {}", userId);

                String sourceFileId = requestBody.get("sourceFileId");
                String destinationFileId = requestBody.get("destinationFileId");

                return storageServiceClient.copyFile(sourceFileId, destinationFileId)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Copy file error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        @PostMapping("/upload/confirm")
        public Mono<ResponseEntity<Object>> confirmUpload(
                        @RequestHeader("X-User-Id") String userId,
                        @RequestBody Map<String, Object> requestBody) {
                log.info("Confirm upload request, userId: {}", userId);

                String fileId = (String) requestBody.get("fileId");
                Number versionObj = (Number) requestBody.get("version");
                int version = versionObj != null ? versionObj.intValue() : 1;
                String hash = (String) requestBody.get("hash");

                return storageServiceClient.confirmUpload(fileId, version, hash)
                                .then(Mono.just(ResponseEntity.ok().build()))
                                .onErrorResume(e -> {
                                        log.error("Confirm upload error: {}", e.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .body(new com.gatewayservice.model.ErrorResponse(
                                                                        e.getMessage())));
                                });
        }

        private Map<String, Object> convertUrlResponseToMap(com.filesync.storage.v1.grpc.UrlResponse response) {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("url", response.getUrl());
                map.put("method", response.getMethod());
                map.put("expiresIn", response.getExpiresIn());
                map.put("headers", response.getHeadersMap());
                return map;
        }
}
