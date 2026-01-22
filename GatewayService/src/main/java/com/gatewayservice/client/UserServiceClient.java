package com.gatewayservice.client;

import com.filesync.user.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    public Mono<com.filesync.user.grpc.UserResponse> getUser(String userId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserRequest request = com.filesync.user.grpc.GetUserRequest.newBuilder()
                        .setUserId(userId)
                        .build();
                return userServiceStub.getUser(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting user: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get user info");
            } catch (Exception e) {
                log.error("Unexpected error getting user: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.UserResponse> getUserByEmail(String email) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserByEmailRequest request = com.filesync.user.grpc.GetUserByEmailRequest
                        .newBuilder()
                        .setEmail(email)
                        .build();
                return userServiceStub.getUserByEmail(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting user by email: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(
                        e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "User not found");
            } catch (Exception e) {
                log.error("Unexpected error getting user by email: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.UserResponse> updateUser(String userId, String name, String avatarUrl) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserResponse>) () -> {
            try {
                com.filesync.user.grpc.UpdateUserRequest.Builder builder = com.filesync.user.grpc.UpdateUserRequest
                        .newBuilder()
                        .setUserId(userId);
                if (name != null)
                    builder.setName(name);
                if (avatarUrl != null)
                    builder.setAvatarUrl(avatarUrl);
                return userServiceStub.updateUser(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error updating user: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to update user info");
            } catch (Exception e) {
                log.error("Unexpected error updating user: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> deleteUser(String adminId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.DeleteUserRequest request = com.filesync.user.grpc.DeleteUserRequest.newBuilder()
                        .setUserId(userId)
                        // Note: DeleteUserRequest in user.proto might need AdminID if we want to log
                        // who deleted it
                        // For now assuming the simple definition or we will update proto next.
                        // Let's check proto again. user.proto: message DeleteUserRequest { string
                        // user_id = 1; }
                        // It lacks admin_id. We should probably add it to proto for consistency with
                        // other admin methods.
                        // But for now, sticking to existing proto definition found in previous
                        // `view_file`.
                        // Wait, previous view_file of user.proto showed:
                        // message DeleteUserRequest { string user_id = 1; }
                        // And rpc DeleteUser(DeleteUserRequest) returns (EmptyResponse);
                        // This is a user-facing delete (self-delete)? Or admin?
                        // The proto comments said:
                        // rpc DeleteUser(DeleteUserRequest) returns (EmptyResponse); // User function
                        // We might need a separate AdminDeleteUser OR just reuse this one but we can't
                        // pass adminId.
                        // Ideally we update proto to include adminId or create AdminDeleteUserRequest.
                        // Given we are doing "Full Delete", likely reusing the Logic is fine, but
                        // tracking "Who" deleted it is good.
                        // Let's update the proto to be robust.
                        .build();
                userServiceStub.deleteUser(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error deleting user: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to delete user");
            } catch (Exception e) {
                log.error("Unexpected error deleting user: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.SettingsResponse> getUserSettings(String userId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.SettingsResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserSettingsRequest request = com.filesync.user.grpc.GetUserSettingsRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                return userServiceStub.getUserSettings(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting settings: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get settings");
            } catch (Exception e) {
                log.error("Unexpected error getting settings: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.SettingsResponse> updateUserSettings(String userId,
            String theme, String language, Boolean notificationsEnabled, Boolean emailNotifications,
            Boolean autoSync, Boolean syncOnMobileData) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.SettingsResponse>) () -> {
            try {
                com.filesync.user.grpc.UpdateUserSettingsRequest.Builder builder = com.filesync.user.grpc.UpdateUserSettingsRequest
                        .newBuilder()
                        .setUserId(userId);
                if (theme != null)
                    builder.setTheme(theme);
                if (language != null)
                    builder.setLanguage(language);
                if (notificationsEnabled != null)
                    builder.setNotificationsEnabled(notificationsEnabled);
                if (emailNotifications != null)
                    builder.setEmailNotifications(emailNotifications);
                if (autoSync != null)
                    builder.setAutoSync(autoSync);
                if (syncOnMobileData != null)
                    builder.setSyncOnMobileData(syncOnMobileData);
                return userServiceStub.updateUserSettings(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error updating settings: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to update settings");
            } catch (Exception e) {
                log.error("Unexpected error updating settings: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.QuotaResponse> checkQuota(String userId, long fileSize) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.QuotaResponse>) () -> {
            try {
                com.filesync.user.grpc.CheckQuotaRequest request = com.filesync.user.grpc.CheckQuotaRequest.newBuilder()
                        .setUserId(userId)
                        .setFileSize(fileSize)
                        .build();
                return userServiceStub.checkQuota(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error checking quota: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to check quota");
            } catch (Exception e) {
                log.error("Unexpected error checking quota: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> updateStorageUsed(String userId, long sizeDelta) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.UpdateStorageUsedRequest request = com.filesync.user.grpc.UpdateStorageUsedRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setSizeDelta(sizeDelta)
                        .build();
                userServiceStub.updateStorageUsed(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error updating storage: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to update storage usage");
            } catch (Exception e) {
                log.error("Unexpected error updating storage: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    // Административные функции
    public Mono<com.filesync.user.grpc.UserListResponse> listUsers(String adminId, int page, int pageSize,
            String search, String plan, String sortBy, String sortOrder) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserListResponse>) () -> {
            try {
                com.filesync.user.grpc.ListUsersRequest.Builder builder = com.filesync.user.grpc.ListUsersRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setPage(page)
                        .setPageSize(pageSize)
                        .setSortBy(sortBy != null ? sortBy : "created_at")
                        .setSortOrder(sortOrder != null ? sortOrder : "desc");
                if (search != null)
                    builder.setSearch(search);
                if (plan != null)
                    builder.setPlan(plan);
                return userServiceStub.listUsers(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error listing users: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to list users");
            } catch (Exception e) {
                log.error("Unexpected error listing users: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.UserDetailsResponse> getUserDetails(String adminId, String userId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserDetailsResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserDetailsRequest request = com.filesync.user.grpc.GetUserDetailsRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .build();
                return userServiceStub.getUserDetails(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting user details: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get user details");
            } catch (Exception e) {
                log.error("Unexpected error getting user details: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> updateUserQuota(String adminId, String userId, long newQuota) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.UpdateUserQuotaRequest request = com.filesync.user.grpc.UpdateUserQuotaRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .setNewQuota(newQuota)
                        .build();
                userServiceStub.updateUserQuota(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error updating quota: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to update user quota");
            } catch (Exception e) {
                log.error("Unexpected error updating quota: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> changeUserPlan(String adminId, String userId, String newPlan) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.ChangeUserPlanRequest request = com.filesync.user.grpc.ChangeUserPlanRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .setNewPlan(newPlan)
                        .build();
                userServiceStub.changeUserPlan(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error changing plan: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to change user plan");
            } catch (Exception e) {
                log.error("Unexpected error changing plan: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.SystemStatsResponse> getSystemStatistics(String adminId,
            Long fromTimestamp, Long toTimestamp) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.SystemStatsResponse>) () -> {
            try {
                com.filesync.user.grpc.GetSystemStatisticsRequest.Builder builder = com.filesync.user.grpc.GetSystemStatisticsRequest
                        .newBuilder()
                        .setAdminId(adminId);
                if (fromTimestamp != null)
                    builder.setFromTimestamp(fromTimestamp);
                if (toTimestamp != null)
                    builder.setToTimestamp(toTimestamp);
                return userServiceStub.getSystemStatistics(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting system stats: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get system statistics");
            } catch (Exception e) {
                log.error("Unexpected error getting system stats: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.StorageStatsResponse> getStorageStatistics(String adminId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.StorageStatsResponse>) () -> {
            try {
                com.filesync.user.grpc.GetStorageStatisticsRequest request = com.filesync.user.grpc.GetStorageStatisticsRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .build();
                return userServiceStub.getStorageStatistics(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting storage stats: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get storage statistics");
            } catch (Exception e) {
                log.error("Unexpected error getting storage stats: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.UserStatsResponse> getUserStatistics(String adminId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserStatsResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserStatisticsRequest request = com.filesync.user.grpc.GetUserStatisticsRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .build();
                return userServiceStub.getUserStatistics(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting user stats: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get user statistics");
            } catch (Exception e) {
                log.error("Unexpected error getting user stats: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.ActiveUsersResponse> getActiveUsers(String adminId, int minutes) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.ActiveUsersResponse>) () -> {
            try {
                com.filesync.user.grpc.GetActiveUsersRequest request = com.filesync.user.grpc.GetActiveUsersRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setMinutes(minutes)
                        .build();
                return userServiceStub.getActiveUsers(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting active users: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get active users");
            } catch (Exception e) {
                log.error("Unexpected error getting active users: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    private com.filesync.user.grpc.UserServiceGrpc.UserServiceBlockingStub getStubWithToken(String token) {
        if (token == null || token.isEmpty()) {
            return userServiceStub;
        }
        io.grpc.Metadata metadata = new io.grpc.Metadata();
        metadata.put(io.grpc.Metadata.Key.of("Authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER), token);
        return userServiceStub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    public Mono<Void> assignUserRole(String adminId, String userId, String roleName, String token) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.AssignUserRoleRequest request = com.filesync.user.grpc.AssignUserRoleRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .setRoleName(roleName)
                        .build();
                getStubWithToken(token).assignUserRole(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error assigning role: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to assign role");
            } catch (Exception e) {
                log.error("Unexpected error assigning role: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> revokeUserRole(String adminId, String userId, String roleName, String token) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.RevokeUserRoleRequest request = com.filesync.user.grpc.RevokeUserRoleRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .setRoleName(roleName)
                        .build();
                getStubWithToken(token).revokeUserRole(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error revoking role: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to revoke role");
            } catch (Exception e) {
                log.error("Unexpected error revoking role: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<com.filesync.user.grpc.UserRolesResponse> getUserRoles(String userId) {
        return Mono.fromCallable((Callable<com.filesync.user.grpc.UserRolesResponse>) () -> {
            try {
                com.filesync.user.grpc.GetUserRolesRequest request = com.filesync.user.grpc.GetUserRolesRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                return userServiceStub.getUserRoles(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting roles: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get user roles");
            } catch (Exception e) {
                log.error("Unexpected error getting roles: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> blockUser(String adminId, String userId, String reason, String token) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.BlockUserRequest request = com.filesync.user.grpc.BlockUserRequest.newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .setReason(reason)
                        .build();
                getStubWithToken(token).blockUser(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error blocking user: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to block user");
            } catch (Exception e) {
                log.error("Unexpected error blocking user: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }

    public Mono<Void> unblockUser(String adminId, String userId, String token) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.filesync.user.grpc.UnblockUserRequest request = com.filesync.user.grpc.UnblockUserRequest
                        .newBuilder()
                        .setAdminId(adminId)
                        .setUserId(userId)
                        .build();
                getStubWithToken(token).unblockUser(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error unblocking user: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to unblock user");
            } catch (Exception e) {
                log.error("Unexpected error unblocking user: {}", e.getMessage(), e);
                throw new RuntimeException("User service error");
            }
        });
    }
}
