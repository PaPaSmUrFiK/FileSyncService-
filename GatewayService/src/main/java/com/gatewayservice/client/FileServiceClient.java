package com.gatewayservice.client;

import com.fileservice.grpc.FileServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceClient {

    @GrpcClient("file-service")
    private FileServiceGrpc.FileServiceBlockingStub fileServiceStub;

    public Mono<com.fileservice.grpc.FileMetadata> createFile(String userId, String name, String path,
            long size, String mimeType, String hash, boolean isFolder, String parentFolderId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileMetadata>) () -> {
            try {
                com.fileservice.grpc.CreateFileRequest.Builder builder = com.fileservice.grpc.CreateFileRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setName(name)
                        .setPath(path)
                        .setSize(size)
                        .setMimeType(mimeType)
                        .setHash(hash != null ? hash : "")
                        .setIsFolder(isFolder);
                if (parentFolderId != null && !parentFolderId.isEmpty()) {
                    builder.setParentFolderId(parentFolderId);
                }
                return fileServiceStub.createFile(builder.build());
            } catch (Exception e) {
                log.error("Error creating file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileMetadata> getFile(String fileId, String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileMetadata>) () -> {
            try {
                com.fileservice.grpc.GetFileRequest request = com.fileservice.grpc.GetFileRequest.newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .build();
                return fileServiceStub.getFile(request);
            } catch (Exception e) {
                log.error("Error getting file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileMetadata> updateFile(String fileId, String userId,
            String name, Long size, String hash, Integer version) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileMetadata>) () -> {
            try {
                com.fileservice.grpc.UpdateFileRequest.Builder builder = com.fileservice.grpc.UpdateFileRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId);
                if (name != null)
                    builder.setName(name);
                if (size != null)
                    builder.setSize(size);
                if (hash != null)
                    builder.setHash(hash);
                if (version != null)
                    builder.setVersion(version);
                return fileServiceStub.updateFile(builder.build());
            } catch (Exception e) {
                log.error("Error updating file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> deleteFile(String fileId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.fileservice.grpc.DeleteFileRequest request = com.fileservice.grpc.DeleteFileRequest.newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .build();
                fileServiceStub.deleteFile(request);
                return null;
            } catch (Exception e) {
                log.error("Error deleting file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileListResponse> listFiles(String userId, String path,
            String parentFolderId, String search, int limit, int offset) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileListResponse>) () -> {
            try {
                com.fileservice.grpc.ListFilesRequest.Builder builder = com.fileservice.grpc.ListFilesRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setLimit(limit)
                        .setOffset(offset);
                if (path != null)
                    builder.setPath(path);
                if (parentFolderId != null)
                    builder.setParentFolderId(parentFolderId);
                if (search != null && !search.isEmpty())
                    builder.setSearchQuery(search);
                return fileServiceStub.listFiles(builder.build());
            } catch (Exception e) {
                log.error("Error listing files via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileListResponse> listTrash(String userId, int limit, int offset) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileListResponse>) () -> {
            try {
                com.fileservice.grpc.ListTrashRequest request = com.fileservice.grpc.ListTrashRequest.newBuilder()
                        .setUserId(userId)
                        .setLimit(limit)
                        .setOffset(offset)
                        .build();
                return fileServiceStub.listTrash(request);
            } catch (Exception e) {
                log.error("Error listing trash via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> restoreFile(String fileId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.fileservice.grpc.RestoreFileRequest request = com.fileservice.grpc.RestoreFileRequest.newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .build();
                fileServiceStub.restoreFile(request);
                return null;
            } catch (Exception e) {
                log.error("Error restoring file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> emptyTrash(String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.fileservice.grpc.EmptyTrashRequest request = com.fileservice.grpc.EmptyTrashRequest.newBuilder()
                        .setUserId(userId)
                        .build();
                fileServiceStub.emptyTrash(request);
                return null;
            } catch (Exception e) {
                log.error("Error emptying trash via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.ShareResponse> shareFile(String fileId, String ownerId,
            String sharedWithUserId, String permission) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.ShareResponse>) () -> {
            try {
                com.fileservice.grpc.ShareFileRequest request = com.fileservice.grpc.ShareFileRequest.newBuilder()
                        .setFileId(fileId)
                        .setOwnerId(ownerId)
                        .setSharedWithUserId(sharedWithUserId)
                        .setPermission(permission)
                        .build();
                return fileServiceStub.shareFile(request);
            } catch (Exception e) {
                log.error("Error sharing file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.VersionListResponse> getFileVersions(String fileId, String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.VersionListResponse>) () -> {
            try {
                com.fileservice.grpc.GetVersionsRequest request = com.fileservice.grpc.GetVersionsRequest.newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .build();
                return fileServiceStub.getFileVersions(request);
            } catch (Exception e) {
                log.error("Error getting file versions via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileMetadata> restoreVersion(String fileId, int version, String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileMetadata>) () -> {
            try {
                com.fileservice.grpc.RestoreVersionRequest request = com.fileservice.grpc.RestoreVersionRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setVersion(version)
                        .setUserId(userId)
                        .build();
                return fileServiceStub.restoreVersion(request);
            } catch (Exception e) {
                log.error("Error restoring version via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.PermissionResponse> checkPermission(String fileId, String userId,
            String requiredPermission) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.PermissionResponse>) () -> {
            try {
                com.fileservice.grpc.CheckPermissionRequest request = com.fileservice.grpc.CheckPermissionRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .setRequiredPermission(requiredPermission)
                        .build();
                return fileServiceStub.checkPermission(request);
            } catch (Exception e) {
                log.error("Error checking permission via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileMetadata> moveFile(String fileId, String newParentFolderId, String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileMetadata>) () -> {
            try {
                com.fileservice.grpc.MoveFileRequest request = com.fileservice.grpc.MoveFileRequest.newBuilder()
                        .setFileId(fileId)
                        .setNewParentFolderId(newParentFolderId != null ? newParentFolderId : "")
                        .setUserId(userId)
                        .build();
                return fileServiceStub.moveFile(request);
            } catch (Exception e) {
                log.error("Error moving file via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileListResponse> listSharedWithMe(String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileListResponse>) () -> {
            try {
                com.fileservice.grpc.ListSharedWithMeRequest request = com.fileservice.grpc.ListSharedWithMeRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                return fileServiceStub.listSharedWithMe(request);
            } catch (Exception e) {
                log.error("Error listing shared with me files via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.ShareListResponse> listMyShares(String ownerId, String fileId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.ShareListResponse>) () -> {
            try {
                com.fileservice.grpc.ListMySharesRequest request = com.fileservice.grpc.ListMySharesRequest.newBuilder()
                        .setOwnerId(ownerId)
                        .setFileId(fileId)
                        .build();
                return fileServiceStub.listMyShares(request);
            } catch (Exception e) {
                log.error("Error listing my shares via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Void> revokeShare(String shareId, String ownerId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.fileservice.grpc.RevokeShareRequest request = com.fileservice.grpc.RevokeShareRequest.newBuilder()
                        .setShareId(shareId)
                        .setOwnerId(ownerId)
                        .build();
                fileServiceStub.revokeShare(request);
                return null;
            } catch (Exception e) {
                log.error("Error revoking share via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileAccessContextResponse> getFileAccessContext(String fileId, String userId) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileAccessContextResponse>) () -> {
            try {
                com.fileservice.grpc.GetFileAccessContextRequest request = com.fileservice.grpc.GetFileAccessContextRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .build();
                return fileServiceStub.getFileAccessContext(request);
            } catch (Exception e) {
                log.error("Error getting file access context via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }

    public Mono<com.fileservice.grpc.FileVersion> addFileVersion(String fileId, String userId, long size, String hash) {
        return Mono.fromCallable((Callable<com.fileservice.grpc.FileVersion>) () -> {
            try {
                com.fileservice.grpc.AddFileVersionRequest request = com.fileservice.grpc.AddFileVersionRequest
                        .newBuilder()
                        .setFileId(fileId)
                        .setUserId(userId)
                        .setSize(size)
                        .setHash(hash != null ? hash : "")
                        .build();
                return fileServiceStub.addFileVersion(request);
            } catch (Exception e) {
                log.error("Error adding file version via gRPC: {}", e.getMessage(), e);
                throw new RuntimeException("File service unavailable: " + e.getMessage(), e);
            }
        });
    }
}
