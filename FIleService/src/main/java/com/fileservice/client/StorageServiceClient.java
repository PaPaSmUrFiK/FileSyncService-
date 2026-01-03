package com.fileservice.client;

import com.storageservice.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class StorageServiceClient {

    @GrpcClient("storage-service")
    private StorageServiceGrpc.StorageServiceBlockingStub storageServiceStub;

    public void confirmUpload(String fileId, boolean success, String storagePath) {
        ConfirmUploadRequest request = ConfirmUploadRequest.newBuilder()
                .setFileId(fileId)
                .setSuccess(success)
                .setStoragePath(storagePath)
                .build();

        try {
            storageServiceStub.confirmUpload(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to confirm upload with storage service", e);
        }
    }

    public String copyFile(String sourcePath, String destinationPath) {
        CopyFileRequest request = CopyFileRequest.newBuilder()
                .setSourcePath(sourcePath)
                .setDestinationPath(destinationPath)
                .build();

        try {
            CopyFileResponse response = storageServiceStub.copyFile(request);
            if (!response.getSuccess()) {
                throw new RuntimeException("Storage service failed to copy file");
            }
            return response.getNewStoragePath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to call copy file on storage service", e);
        }
    }

    public void deleteFile(String storagePath) {
        if (storagePath == null || storagePath.isEmpty()) {
            return;
        }
        DeleteFileRequest request = DeleteFileRequest.newBuilder()
                .setStoragePath(storagePath)
                .build();

        try {
            storageServiceStub.deleteFile(request);
        } catch (Exception e) {
            // Cleanup failures shouldn't necessarily fail the main transaction,
            // but we'll log it. For now throwing runtime exception as per strict
            // requirement interpretation.
            throw new RuntimeException("Failed to delete file on storage service", e);
        }
    }

    public String getUploadUrl(String fileId) {
        GetUploadUrlRequest request = GetUploadUrlRequest.newBuilder()
                .setFileId(fileId)
                .build();
        try {
            GetUploadUrlResponse response = storageServiceStub.getUploadUrl(request);
            return response.getUploadUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get upload url from storage service", e);
        }
    }

    public String getDownloadUrl(String fileId) {
        GetDownloadUrlRequest request = GetDownloadUrlRequest.newBuilder()
                .setFileId(fileId)
                .build();
        try {
            GetDownloadUrlResponse response = storageServiceStub.getDownloadUrl(request);
            return response.getDownloadUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get download url from storage service", e);
        }
    }
}
