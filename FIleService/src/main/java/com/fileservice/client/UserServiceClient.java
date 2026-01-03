package com.fileservice.client;

import com.userservice.grpc.CheckQuotaRequest;
import com.userservice.grpc.CheckQuotaResponse;
import com.userservice.grpc.UpdateStorageUsedRequest;
import com.userservice.grpc.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public boolean checkQuota(UUID userId, long size) {
        CheckQuotaRequest request = CheckQuotaRequest.newBuilder()
                .setUserId(userId.toString())
                .setSize(size)
                .build();

        try {
            CheckQuotaResponse response = userServiceStub.checkQuota(request);
            return response.getAllowed();
        } catch (Exception e) {
            // In case of error (e.g. user service down), we should decide policy.
            // For now, let's assume fail-safe or rethrow.
            // Requirements say strict contracts, but we don't want to block uploads if user
            // service hiccups?
            // Actually, quota check is critical.
            throw new RuntimeException("Failed to check quota with user service", e);
        }
    }

    public void updateStorageUsed(UUID userId, long delta) {
        UpdateStorageUsedRequest request = UpdateStorageUsedRequest.newBuilder()
                .setUserId(userId.toString())
                .setDelta(delta)
                .build();

        try {
            userServiceStub.updateStorageUsed(request);
        } catch (Exception e) {
            // This ideally should be retried or sent to a DLQ/Kafka if sync call fails.
            // But required is gRPC client.
            throw new RuntimeException("Failed to update storage usage", e);
        }
    }
}
