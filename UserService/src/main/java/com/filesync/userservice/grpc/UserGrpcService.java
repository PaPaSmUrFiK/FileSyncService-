package com.filesync.userservice.grpc;

import com.filesync.user.grpc.*;
import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.model.domain.UserSettings;
import com.filesync.userservice.service.StatisticsService;
import com.filesync.userservice.service.UserService;
import com.filesync.userservice.service.integration.AuthServiceClient;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;
    private final StatisticsService statisticsService;
    private final AuthServiceClient authServiceClient;

    // --- User Operations ---

    @Override
    public void getUser(GetUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userService.getUser(UUID.fromString(request.getUserId()));
            responseObserver.onNext(mapUserToResponse(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateUser(UpdateUserRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userService.updateUser(
                    UUID.fromString(request.getUserId()),
                    request.hasName() ? request.getName() : null,
                    request.hasAvatarUrl() ? request.getAvatarUrl() : null);
            responseObserver.onNext(mapUserToResponse(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void deleteUser(DeleteUserRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            userService.deleteUser(UUID.fromString(request.getUserId()));
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getUserSettings(GetUserSettingsRequest request, StreamObserver<SettingsResponse> responseObserver) {
        try {
            UserSettings settings = userService.getUserSettings(UUID.fromString(request.getUserId()));
            responseObserver.onNext(mapSettingsToResponse(settings));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateUserSettings(UpdateUserSettingsRequest request,
            StreamObserver<SettingsResponse> responseObserver) {
        try {
            UserSettings newSettings = new UserSettings();
            if (request.hasTheme())
                newSettings.setTheme(request.getTheme());
            if (request.hasLanguage())
                newSettings.setLanguage(request.getLanguage());
            if (request.hasNotificationsEnabled())
                newSettings.setNotificationsEnabled(request.getNotificationsEnabled());
            if (request.hasEmailNotifications())
                newSettings.setEmailNotifications(request.getEmailNotifications());
            if (request.hasAutoSync())
                newSettings.setAutoSync(request.getAutoSync());
            if (request.hasSyncOnMobileData())
                newSettings.setSyncOnMobileData(request.getSyncOnMobileData());

            UserSettings updated = userService.updateUserSettings(UUID.fromString(request.getUserId()), newSettings);
            responseObserver.onNext(mapSettingsToResponse(updated));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void checkQuota(CheckQuotaRequest request, StreamObserver<QuotaResponse> responseObserver) {
        try {
            UserQuota quota = userService.checkQuota(UUID.fromString(request.getUserId()), request.getFileSize());
            User user = quota.getUser();

            long available = user.getStorageQuota() - user.getStorageUsed();
            boolean hasSpace = available >= request.getFileSize();

            QuotaResponse response = QuotaResponse.newBuilder()
                    .setHasSpace(hasSpace)
                    .setAvailableSpace(available)
                    .setStorageUsed(user.getStorageUsed())
                    .setStorageQuota(user.getStorageQuota())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateStorageUsed(UpdateStorageUsedRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            userService.updateStorageUsed(UUID.fromString(request.getUserId()), request.getSizeDelta());
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // --- Admin Operations ---

    @Override
    public void listUsers(ListUsersRequest request, StreamObserver<UserListResponse> responseObserver) {
        try {
            int page = request.getPage() > 0 ? request.getPage() : 0;
            int size = request.getPageSize() > 0 ? request.getPageSize() : 10;
            String sortBy = request.getSortBy().isEmpty() ? "createdAt" : request.getSortBy();
            Sort.Direction dir = "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            Page<User> userPage = userService.listUsers(
                    request.hasSearch() ? request.getSearch() : null,
                    request.hasPlan() ? request.getPlan() : null,
                    PageRequest.of(page, size, Sort.by(dir, sortBy)));

            List<UserInfo> userInfos = userPage.getContent().stream()
                    .map(this::mapUserToInfo)
                    .collect(Collectors.toList());

            UserListResponse response = UserListResponse.newBuilder()
                    .addAllUsers(userInfos)
                    .setTotal((int) userPage.getTotalElements())
                    .setPage(userPage.getNumber())
                    .setPageSize(userPage.getSize())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getSystemStatistics(GetSystemStatisticsRequest request,
            StreamObserver<SystemStatsResponse> responseObserver) {
        try {
            Map<String, Object> stats = statisticsService.getSystemStatistics(
                    request.hasFromTimestamp() ? request.getFromTimestamp() : null,
                    request.hasToTimestamp() ? request.getToTimestamp() : null);

            SystemStatsResponse.Builder builder = SystemStatsResponse.newBuilder();
            if (stats.containsKey("total_users"))
                builder.setTotalUsers((Integer) stats.get("total_users"));
            if (stats.containsKey("active_users_today"))
                builder.setActiveUsersToday((Integer) stats.get("active_users_today"));
            if (stats.containsKey("active_users_week"))
                builder.setActiveUsersWeek((Integer) stats.get("active_users_week"));
            if (stats.containsKey("active_users_month"))
                builder.setActiveUsersMonth((Integer) stats.get("active_users_month"));
            if (stats.containsKey("new_users_today"))
                builder.setNewUsersToday((Integer) stats.get("new_users_today"));
            if (stats.containsKey("new_users_week"))
                builder.setNewUsersWeek((Integer) stats.get("new_users_week"));
            if (stats.containsKey("new_users_month"))
                builder.setNewUsersMonth((Integer) stats.get("new_users_month"));
            if (stats.containsKey("total_storage_used"))
                builder.setTotalStorageUsed((Long) stats.get("total_storage_used"));
            if (stats.containsKey("total_storage_allocated"))
                builder.setTotalStorageAllocated((Long) stats.get("total_storage_allocated"));
            if (stats.containsKey("storage_usage_percentage"))
                builder.setStorageUsagePercentage((Double) stats.get("storage_usage_percentage"));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // --- Role / Block Operations (Delegate) ---

    @Override
    public void assignUserRole(AssignUserRoleRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            authServiceClient.assignRole(UUID.fromString(request.getUserId()), request.getRoleName());
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void revokeUserRole(RevokeUserRoleRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            authServiceClient.revokeRole(UUID.fromString(request.getUserId()), request.getRoleName());
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getUserRoles(GetUserRolesRequest request, StreamObserver<UserRolesResponse> responseObserver) {
        try {
            List<String> roles = authServiceClient.getUserRoles(UUID.fromString(request.getUserId()));
            responseObserver.onNext(UserRolesResponse.newBuilder().addAllRoles(roles).build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    // --- Helpers ---

    private UserResponse mapUserToResponse(User user) {
        // Need to fetch roles from Auth service usually, but UserResponse expects
        // roles.
        // Assuming we might skip roles for basic GetUser or fetch them async.
        // For now, empty list or fetch.
        // Let's fetch for completeness if possible, or leave empty if too slow.
        // The prompt implies User service knows everything, but Roles are in Auth.
        // I'll fetch roles.
        List<String> roles;
        try {
            roles = authServiceClient.getUserRoles(user.getId());
        } catch (Exception e) {
            roles = List.of(); // Fallback
        }

        return UserResponse.newBuilder()
                .setId(user.getId().toString())
                .setEmail(user.getEmail())
                .setName(user.getName() != null ? user.getName() : "")
                .setAvatarUrl(user.getAvatarUrl() != null ? user.getAvatarUrl() : "")
                .addAllRoles(roles)
                .setStorageUsed(user.getStorageUsed())
                .setStorageQuota(user.getStorageQuota())
                .setCreatedAt(user.getCreatedAt().toString())
                .setUpdatedAt(user.getUpdatedAt().toString())
                .build();
    }

    private SettingsResponse mapSettingsToResponse(UserSettings settings) {
        return SettingsResponse.newBuilder()
                .setUserId(settings.getUser().getId().toString())
                .setTheme(settings.getTheme())
                .setLanguage(settings.getLanguage())
                .setNotificationsEnabled(settings.getNotificationsEnabled())
                .setEmailNotifications(settings.getEmailNotifications())
                .setAutoSync(settings.getAutoSync())
                .setSyncOnMobileData(settings.getSyncOnMobileData())
                .build();
    }

    private UserInfo mapUserToInfo(User user) {
        return UserInfo.newBuilder()
                .setId(user.getId().toString())
                .setEmail(user.getEmail())
                .setName(user.getName() != null ? user.getName() : "")
                .setStorageUsed(user.getStorageUsed())
                .setStorageQuota(user.getStorageQuota())
                .setCreatedAt(user.getCreatedAt().toString())
                // .setLastLoginAt() // Needed from Auth
                .build();
    }
}
