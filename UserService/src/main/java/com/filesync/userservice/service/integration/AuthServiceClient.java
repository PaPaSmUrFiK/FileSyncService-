package com.filesync.userservice.service.integration;

import com.authservice.grpc.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final AuthServiceGrpc.AuthServiceBlockingStub authServiceBlockingStub;

    public void assignRole(UUID userId, String roleName) {
        authServiceBlockingStub.assignRole(AssignRoleRequest.newBuilder()
                .setUserId(userId.toString())
                .setRoleName(roleName)
                .build());
    }

    public void revokeRole(UUID userId, String roleName) {
        authServiceBlockingStub.revokeRole(RevokeRoleRequest.newBuilder()
                .setUserId(userId.toString())
                .setRoleName(roleName)
                .build());
    }

    public List<String> getUserRoles(UUID userId) {
        try {
            log.debug("Calling AuthService.getUserRoles for user: {}", userId);
            UserRolesResponse response = authServiceBlockingStub.getUserRoles(GetUserRolesRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build());
            log.debug("AuthService returned {} roles for user {}: {}",
                    response.getRolesCount(), userId, response.getRolesList());
            return response.getRolesList();
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("gRPC error calling getUserRoles for user {}: {} - {}",
                    userId, e.getStatus(), e.getMessage());
            throw new RuntimeException("Failed to get user roles from AuthService: " + e.getStatus().getDescription(),
                    e);
        } catch (Exception e) {
            log.error("Unexpected error calling getUserRoles for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to get user roles from AuthService", e);
        }
    }

    public int getActiveUsersCount(LocalDateTime from, LocalDateTime to) {
        try {
            long fromTs = from.atZone(ZoneId.systemDefault()).toEpochSecond();
            long toTs = to.atZone(ZoneId.systemDefault()).toEpochSecond();

            CountResponse response = authServiceBlockingStub.getActiveUsersCount(
                    TimeRangeRequest.newBuilder()
                            .setFromTimestamp(fromTs)
                            .setToTimestamp(toTs)
                            .build());
            return response.getCount();
        } catch (Exception e) {
            // Log error, return 0 or rethrow. For stats, 0 might be safer to keep app
            // running
            return 0;
        }
    }

    public List<String> getUsersActiveInLastMinutes(int minutes) {
        try {
            UserIdsResponse response = authServiceBlockingStub.getUsersActiveInLastMinutes(
                    TimeWindowRequest.newBuilder()
                            .setMinutes(minutes)
                            .build());
            return response.getUserIdsList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public void blockUser(UUID userId, String reason) {
        try {
            authServiceBlockingStub.blockUser(BlockUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .setReason(reason)
                    .build());
        } catch (io.grpc.StatusRuntimeException e) {
            throw new RuntimeException("Failed to block user in AuthService: " + e.getStatus().getDescription(), e);
        }
    }

    public void unblockUser(UUID userId) {
        authServiceBlockingStub.unblockUser(UnblockUserRequest.newBuilder()
                .setUserId(userId.toString())
                .build());
    }

    public void deleteUser(UUID userId) {
        authServiceBlockingStub.deleteUser(DeleteUserRequest.newBuilder()
                .setUserId(userId.toString())
                .build());
    }

    public int getAdminCount() {
        try {
            CountResponse response = authServiceBlockingStub.getAdminCount(GetAdminCountRequest.newBuilder().build());
            return response.getCount();
        } catch (Exception e) {
            log.warn("Failed to get admin count from AuthService, defaulting to 1. Error: {}", e.getMessage());
            return 1;
        }
    }

    public int getBlockedUsersCount() {
        try {
            CountResponse response = authServiceBlockingStub
                    .getBlockedUsersCount(GetBlockedUsersCountRequest.newBuilder().build());
            return response.getCount();
        } catch (Exception e) {
            log.warn("Failed to get blocked users count from AuthService, defaulting to 0. Error: {}", e.getMessage());
            return 0;
        }
    }
}
