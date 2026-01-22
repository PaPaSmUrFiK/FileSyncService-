package com.fileservice.client;

import com.filesync.user.grpc.CheckQuotaRequest;
import com.filesync.user.grpc.QuotaResponse;
import com.filesync.user.grpc.UpdateStorageUsedRequest;
import com.filesync.user.grpc.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public boolean checkQuota(UUID userId, long size) {
        CheckQuotaRequest request = CheckQuotaRequest.newBuilder()
                .setUserId(userId.toString())
                .setFileSize(size)
                .build();

        try {
            log.debug("Checking quota for user {} with file size {}", userId, size);
            QuotaResponse response = userServiceStub.checkQuota(request);
            boolean hasSpace = response.getHasSpace();
            log.debug("Quota check result for user {}: hasSpace={}, available={}, used={}, total={}",
                    userId, hasSpace, response.getAvailableSpace(), response.getStorageUsed(),
                    response.getStorageQuota());
            return hasSpace;
        } catch (StatusRuntimeException e) {
            Status status = e.getStatus();
            String errorMessage = status.getDescription() != null ? status.getDescription() : status.getCode().name();
            log.error("gRPC error checking quota for user {}: {} - {}", userId, status.getCode(), errorMessage);

            // Если ошибка связана с недоступностью сервиса, бросаем понятное исключение
            if (status.getCode() == Status.Code.UNAVAILABLE || status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw new RuntimeException("User service is unavailable. Please try again later.", e);
            }

            // Для остальных ошибок (например, INTERNAL) пробрасываем оригинальное сообщение
            throw new RuntimeException("Failed to check quota: " + errorMessage, e);
        } catch (Exception e) {
            log.error("Unexpected error checking quota for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to check quota with user service: " + e.getMessage(), e);
        }
    }

    public void updateStorageUsed(UUID userId, long delta) {
        UpdateStorageUsedRequest request = UpdateStorageUsedRequest.newBuilder()
                .setUserId(userId.toString())
                .setSizeDelta(delta)
                .build();

        try {
            userServiceStub.updateStorageUsed(request);
        } catch (Exception e) {
            // This ideally should be retried or sent to a DLQ/Kafka if sync call fails.
            // But required is gRPC client.
            throw new RuntimeException("Failed to update storage usage", e);
        }
    }

    /**
     * Get user information (email and name) by user ID
     * Returns null if user not found
     */
    public UserInfo getUserInfo(UUID userId) {
        com.filesync.user.grpc.GetUserRequest request = com.filesync.user.grpc.GetUserRequest.newBuilder()
                .setUserId(userId.toString())
                .build();

        try {
            com.filesync.user.grpc.UserResponse response = userServiceStub.getUser(request);
            return new UserInfo(response.getEmail(), response.getName());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.warn("User not found: {}", userId);
                return null;
            }
            log.error("Error getting user info for {}: {}", userId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error getting user info for {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Simple DTO for user information
     */
    public static class UserInfo {
        private final String email;
        private final String name;

        public UserInfo(String email, String name) {
            this.email = email;
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}
