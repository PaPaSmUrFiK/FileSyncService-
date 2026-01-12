package com.gatewayservice.client;

import com.filesync.sync.v1.grpc.SyncServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncServiceClient {

    @GrpcClient("sync-service")
    private SyncServiceGrpc.SyncServiceBlockingStub syncServiceStub;

    public Mono<com.filesync.sync.v1.grpc.DeviceResponse> registerDevice(String userId, String deviceName,
            String deviceType, String os, String osVersion) {
        return Mono.fromCallable((Callable<com.filesync.sync.v1.grpc.DeviceResponse>) () -> {
            try {
                com.filesync.sync.v1.grpc.RegisterDeviceRequest request = 
                        com.filesync.sync.v1.grpc.RegisterDeviceRequest.newBuilder()
                        .setUserId(userId)
                        .setDeviceName(deviceName)
                        .setDeviceType(deviceType)
                        .setOs(os)
                        .setOsVersion(osVersion)
                        .build();
                return syncServiceStub.registerDevice(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error during device registration: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Device registration failed");
            } catch (Exception e) {
                log.error("Unexpected error during device registration: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<com.filesync.sync.v1.grpc.SyncStatusResponse> getSyncStatus(String deviceId, String userId) {
        return Mono.fromCallable((Callable<com.filesync.sync.v1.grpc.SyncStatusResponse>) () -> {
            try {
                com.filesync.sync.v1.grpc.SyncStatusRequest request = 
                        com.filesync.sync.v1.grpc.SyncStatusRequest.newBuilder()
                        .setDeviceId(deviceId)
                        .setUserId(userId)
                        .build();
                return syncServiceStub.getSyncStatus(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting sync status: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Failed to get sync status");
            } catch (Exception e) {
                log.error("Unexpected error getting sync status: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<com.filesync.sync.v1.grpc.PushChangesResponse> pushChanges(String deviceId, String userId,
            List<FileChange> changes) {
        return Mono.fromCallable((Callable<com.filesync.sync.v1.grpc.PushChangesResponse>) () -> {
            try {
                com.filesync.sync.v1.grpc.PushChangesRequest.Builder builder = 
                        com.filesync.sync.v1.grpc.PushChangesRequest.newBuilder()
                        .setDeviceId(deviceId)
                        .setUserId(userId);
                
                for (FileChange change : changes) {
                    com.filesync.sync.v1.grpc.FileChange.Builder changeBuilder = 
                            com.filesync.sync.v1.grpc.FileChange.newBuilder()
                            .setFileId(change.fileId)
                            .setFilePath(change.filePath)
                            .setChangeType(change.changeType)
                            .setFileHash(change.fileHash != null ? change.fileHash : "")
                            .setFileSize(change.fileSize)
                            .setLocalVersion(change.localVersion)
                            .setClientTimestamp(change.clientTimestamp);
                    if (change.oldPath != null) {
                        changeBuilder.setOldPath(change.oldPath);
                    }
                    builder.addChanges(changeBuilder.build());
                }
                
                return syncServiceStub.pushChanges(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error pushing changes: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Failed to push changes");
            } catch (Exception e) {
                log.error("Unexpected error pushing changes: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<com.filesync.sync.v1.grpc.PullChangesResponse> pullChanges(String deviceId, String userId,
            String lastSyncCursor) {
        return Mono.fromCallable((Callable<com.filesync.sync.v1.grpc.PullChangesResponse>) () -> {
            try {
                com.filesync.sync.v1.grpc.PullChangesRequest.Builder builder = 
                        com.filesync.sync.v1.grpc.PullChangesRequest.newBuilder()
                        .setDeviceId(deviceId)
                        .setUserId(userId);
                if (lastSyncCursor != null) {
                    builder.setLastSyncCursor(lastSyncCursor);
                }
                return syncServiceStub.pullChanges(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error pulling changes: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Failed to pull changes");
            } catch (Exception e) {
                log.error("Unexpected error pulling changes: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<Void> resolveConflict(String conflictId, String userId, String resolutionType,
            String chosenFileId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.sync.v1.grpc.ConflictResolutionRequest.Builder builder = 
                        com.filesync.sync.v1.grpc.ConflictResolutionRequest.newBuilder()
                        .setConflictId(conflictId)
                        .setUserId(userId)
                        .setResolutionType(resolutionType);
                if (chosenFileId != null) {
                    builder.setChosenFileId(chosenFileId);
                }
                syncServiceStub.resolveConflict(builder.build());
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error resolving conflict: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Conflict resolution failed");
            } catch (Exception e) {
                log.error("Unexpected error resolving conflict: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<Void> unregisterDevice(String deviceId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.sync.v1.grpc.UnregisterDeviceRequest request = 
                        com.filesync.sync.v1.grpc.UnregisterDeviceRequest.newBuilder()
                        .setDeviceId(deviceId)
                        .setUserId(userId)
                        .build();
                syncServiceStub.unregisterDevice(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error unregistering device: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Failed to unregister device");
            } catch (Exception e) {
                log.error("Unexpected error unregistering device: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    public Mono<com.filesync.sync.v1.grpc.DevicesListResponse> getDevices(String userId) {
        return Mono.fromCallable((Callable<com.filesync.sync.v1.grpc.DevicesListResponse>) () -> {
            try {
                com.filesync.sync.v1.grpc.GetDevicesRequest request = 
                        com.filesync.sync.v1.grpc.GetDevicesRequest.newBuilder()
                        .setUserId(userId)
                        .build();
                return syncServiceStub.getDevices(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting devices: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Failed to get devices");
            } catch (Exception e) {
                log.error("Unexpected error getting devices: {}", e.getMessage(), e);
                throw new RuntimeException("Sync service error");
            }
        });
    }

    // Вспомогательный класс для передачи изменений
    public static class FileChange {
        public String fileId;
        public String filePath;
        public String changeType;
        public String fileHash;
        public long fileSize;
        public int localVersion;
        public String clientTimestamp;
        public String oldPath;
    }
}

