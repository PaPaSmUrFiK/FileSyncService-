package com.fileservice.client;

import com.filesync.storage.v1.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class StorageServiceClient {

    @GrpcClient("storage-service")
    private StorageServiceGrpc.StorageServiceBlockingStub storageServiceStub;

    public void confirmUpload(String fileId, int version, String hash) {
        ConfirmUploadRequest request = ConfirmUploadRequest.newBuilder()
                .setFileId(fileId)
                .setVersion(version)
                .setHash(hash)
                .build();

        try {
            storageServiceStub.confirmUpload(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to confirm upload with storage service", e);
        }
    }

    public void copyFile(String sourceFileId, String destinationFileId) {
        CopyFileRequest request = CopyFileRequest.newBuilder()
                .setSourceFileId(sourceFileId)
                .setDestinationFileId(destinationFileId)
                .build();

        try {
            storageServiceStub.copyFile(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call copy file on storage service", e);
        }
    }

    public void deleteFile(String fileId, Integer version) {
        DeleteFileRequest.Builder builder = DeleteFileRequest.newBuilder()
                .setFileId(fileId);

        if (version != null) {
            builder.setVersion(version);
        }

        try {
            storageServiceStub.deleteFile(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file on storage service", e);
        }
    }

    public String getUploadUrl(String fileId, String fileName, long fileSize, String mimeType, int version) {
        UploadUrlRequest request = UploadUrlRequest.newBuilder()
                .setFileId(fileId)
                .setFileName(fileName)
                .setFileSize(fileSize)
                .setMimeType(mimeType)
                .setVersion(version)
                .build();
        try {
            UrlResponse response = storageServiceStub.getUploadUrl(request);
            return response.getUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get upload url from storage service", e);
        }
    }

    public String getDownloadUrl(String fileId, Integer version) {
        DownloadUrlRequest.Builder builder = DownloadUrlRequest.newBuilder()
                .setFileId(fileId);

        if (version != null) {
            builder.setVersion(version);
        }

        try {
            UrlResponse response = storageServiceStub.getDownloadUrl(builder.build());
            return response.getUrl();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get download url from storage service", e);
        }
    }
}
