package com.gatewayservice.client;

import com.filesync.storage.v1.grpc.StorageServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageServiceClient {

    @GrpcClient("storage-service")
    private StorageServiceGrpc.StorageServiceBlockingStub storageServiceStub;

    public Mono<com.filesync.storage.v1.grpc.UrlResponse> getUploadUrl(String fileId, String fileName,
            long fileSize, String mimeType, int version) {
        return Mono.fromCallable((Callable<com.filesync.storage.v1.grpc.UrlResponse>) () -> {
            try {
                com.filesync.storage.v1.grpc.UploadUrlRequest request = com.filesync.storage.v1.grpc.UploadUrlRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setFileName(fileName != null ? fileName : "")
                        .setFileSize(fileSize)
                        .setMimeType(mimeType != null ? mimeType : "application/octet-stream")
                        .setVersion(version)
                        .build();
                com.filesync.storage.v1.grpc.UrlResponse response = storageServiceStub.getUploadUrl(request);
                log.info("Received gRPC response: URL={}, Method={}", response.getUrl(), response.getMethod());
                return response;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting upload URL: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get upload URL");
            } catch (Exception e) {
                log.error("Unexpected error getting upload URL: {}", e.getMessage(), e);
                throw new RuntimeException("Storage service error");
            }
        });
    }

    public Mono<com.filesync.storage.v1.grpc.UrlResponse> getDownloadUrl(String fileId, Integer version,
            String fileName) {
        return Mono.fromCallable((Callable<com.filesync.storage.v1.grpc.UrlResponse>) () -> {
            try {
                com.filesync.storage.v1.grpc.DownloadUrlRequest.Builder builder = com.filesync.storage.v1.grpc.DownloadUrlRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setFileName(fileName != null ? fileName : "");
                if (version != null) {
                    builder.setVersion(version);
                }
                return storageServiceStub.getDownloadUrl(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting download URL: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get download URL");
            } catch (Exception e) {
                log.error("Unexpected error getting download URL: {}", e.getMessage(), e);
                throw new RuntimeException("Storage service error");
            }
        });
    }

    public Mono<Void> deleteFile(String fileId, Integer version) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.storage.v1.grpc.DeleteFileRequest.Builder builder = com.filesync.storage.v1.grpc.DeleteFileRequest
                        .newBuilder()
                        .setFileId(fileId);
                if (version != null) {
                    builder.setVersion(version);
                }
                storageServiceStub.deleteFile(builder.build());
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error deleting file: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to delete file from storage");
            } catch (Exception e) {
                log.error("Unexpected error deleting file: {}", e.getMessage(), e);
                throw new RuntimeException("Storage service error");
            }
        });
    }

    public Mono<Void> copyFile(String sourceFileId, String destinationFileId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.storage.v1.grpc.CopyFileRequest request = com.filesync.storage.v1.grpc.CopyFileRequest
                        .newBuilder()
                        .setSourceFileId(sourceFileId)
                        .setDestinationFileId(destinationFileId)
                        .build();
                storageServiceStub.copyFile(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error copying file: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to copy file in storage");
            } catch (Exception e) {
                log.error("Unexpected error copying file: {}", e.getMessage(), e);
                throw new RuntimeException("Storage service error");
            }
        });
    }

    public Mono<Void> confirmUpload(String fileId, int version, String hash) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.storage.v1.grpc.ConfirmUploadRequest request = com.filesync.storage.v1.grpc.ConfirmUploadRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setVersion(version)
                        .setHash(hash)
                        .build();
                storageServiceStub.confirmUpload(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error confirming upload: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to confirm upload in storage");
            } catch (Exception e) {
                log.error("Unexpected error confirming upload: {}", e.getMessage(), e);
                throw new RuntimeException("Storage service error");
            }
        });
    }
}
