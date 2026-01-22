package com.filesync.userservice.grpc;

import com.filesync.user.grpc.*;
import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.model.domain.UserSettings;
import com.filesync.userservice.repository.UserQuotaRepository;
import com.filesync.userservice.service.StatisticsService;
import com.filesync.userservice.service.UserService;
import com.filesync.userservice.service.integration.AuthServiceClient;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserQuotaRepository userQuotaRepository;

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
    public void getUserByEmail(GetUserByEmailRequest request, StreamObserver<UserResponse> responseObserver) {
        try {
            User user = userService.getUserByEmail(request.getEmail());
            responseObserver.onNext(mapUserToResponse(user));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in getUserByEmail: {}", e.getMessage());
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
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
    @Transactional
    public void checkQuota(CheckQuotaRequest request, StreamObserver<QuotaResponse> responseObserver) {
        final UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
            long requestedSize = request.getFileSize();
            log.debug("Checking quota for user {} with file size {}", userId, requestedSize);

            UserQuota quota = null;
            User user = null;

            // Явная проверка через репозиторий, чтобы избежать исключений, которые ломают
            // транзакцию (@Transactional)
            // userService.checkQuota бросает RuntimeException, что помечает транзакцию как
            // rollback-only
            quota = userQuotaRepository.findByUserId(userId).orElse(null);

            if (quota == null) {
                log.info("User or quota not found for userId={}, creating default profile", userId);
                try {
                    // createUser сам создаст все зависимости (квоту и настройки)
                    // Используем заглушку email, так как в checkQuota приходит только userId
                    // По-хорошему, здесь должен быть запрос к AuthService за деталями, но это может
                    // быть дорого
                    user = userService.createUser(userId, "user-" + userId + "@example.com", "User");
                    quota = userQuotaRepository.findByUserId(userId)
                            .orElseThrow(() -> new RuntimeException(
                                    "Failed to retrieve created quota for user: " + userId));
                } catch (Exception createEx) {
                    log.error("Failed to recover user/quota for userId={}: {}", userId, createEx.getMessage(),
                            createEx);
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("Failed to initialize user quota: " + createEx.getMessage())
                            .asRuntimeException());
                    return;
                }
            } else {
                user = quota.getUser();
            }

            // На этом этапе у нас должны быть и user, и quota
            if (user == null || quota == null) {
                log.error("Critical failure: user or quota is still null after recovery for userId={}", userId);
                responseObserver.onError(io.grpc.Status.INTERNAL
                        .withDescription("Internal error: data missing after quota initialization")
                        .asRuntimeException());
                return;
            }

            // Получаем лимиты
            long currentUsed = user.getStorageUsed() != null ? user.getStorageUsed() : 0L;
            long totalQuota = user.getStorageQuota() != null ? user.getStorageQuota() : 5368709120L; // Default 5GB
            long maxFileLimit = quota.getMaxFileSize() != null ? quota.getMaxFileSize() : 104857600L; // Default 100MB

            long available = totalQuota - currentUsed;

            // Проверка: достаточно ли общего места И не превышает ли файл лимит плана
            boolean hasSpace = (available >= requestedSize);
            boolean isWithinLimit = (requestedSize <= maxFileLimit);

            boolean finalResult = hasSpace && isWithinLimit;

            log.info("Quota check result for user {}: requested={}, available={}, maxFile={}, result={}",
                    userId, requestedSize, available, maxFileLimit, finalResult);

            if (!isWithinLimit && requestedSize > 0) {
                log.warn("File size {} exceeds max file limit {} for user {}", requestedSize, maxFileLimit, userId);
            }

            QuotaResponse response = QuotaResponse.newBuilder()
                    .setHasSpace(finalResult)
                    .setAvailableSpace(Math.max(0, available))
                    .setStorageUsed(currentUsed)
                    .setStorageQuota(totalQuota)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in checkQuota: {}", request.getUserId());
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Invalid user ID format")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Unhandled error in checkQuota for user {}: {}", request.getUserId(), e.getMessage(), e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Internal error checking quota: " + e.getMessage())
                    .asRuntimeException());
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
        log.info("ListUsers called: page={}, size={}, search={}, sortBy={}",
                request.getPage(), request.getPageSize(),
                request.hasSearch() ? request.getSearch() : "none",
                request.getSortBy());
        try {
            int page = request.getPage() > 0 ? request.getPage() - 1 : 0;
            int size = request.getPageSize() > 0 ? request.getPageSize() : 10;

            // Map snake_case to camelCase for entity fields
            String sortBy = request.getSortBy().isEmpty() ? "createdAt" : request.getSortBy();
            if ("created_at".equals(sortBy)) {
                sortBy = "createdAt";
            } else if ("updated_at".equals(sortBy)) {
                sortBy = "updatedAt";
            }

            Sort.Direction dir = "desc".equalsIgnoreCase(request.getSortOrder()) ? Sort.Direction.DESC
                    : Sort.Direction.ASC;

            Page<User> userPage = userService.listUsers(
                    request.hasSearch() ? request.getSearch() : null,
                    request.hasPlan() ? request.getPlan() : null,
                    PageRequest.of(page, size, Sort.by(dir, sortBy)));

            List<UserInfo> userInfos = userPage.getContent().stream()
                    .map(user -> {
                        UserInfo.Builder infoBuilder = mapUserToInfo(user).toBuilder();
                        try {
                            // Populate roles from AuthService
                            var roles = authServiceClient.getUserRoles(user.getId());
                            log.info("Fetched {} roles for user {}: {}", roles != null ? roles.size() : 0, user.getId(),
                                    roles);
                            infoBuilder.clearRoles();
                            if (roles != null && !roles.isEmpty()) {
                                infoBuilder.addAllRoles(roles);
                            } else {
                                // Add default USER role if no roles found
                                log.warn("No roles found for user {}, adding default USER role", user.getId());
                                infoBuilder.addRoles("USER");
                            }
                        } catch (Exception e) {
                            // Suppress error if user not found in Auth Service (might be inconsistent
                            // state, but shouldn't break list)
                            // Log at debug level to reduce noise, unless it's a connection error
                            if (e.getMessage() != null && (e.getMessage().contains("User not found")
                                    || e.getMessage().contains("UNAUTHENTICATED"))) {
                                log.warn("User {} found in UserService but not in AuthService (skipping roles fetch)",
                                        user.getId());
                            } else {
                                log.error("Failed to fetch roles for user {}: {}", user.getId(), e.getMessage());
                            }
                            // Fallback to default USER role
                            infoBuilder.clearRoles();
                            infoBuilder.addRoles("USER");
                        }
                        UserInfo result = infoBuilder.build();
                        log.info("Built UserInfo for {}: roles={}", user.getEmail(), result.getRolesList());
                        return result;
                    })
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
            log.error("Error listing users", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to list users: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
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

    @Override
    public void getStorageStatistics(GetStorageStatisticsRequest request,
            StreamObserver<StorageStatsResponse> responseObserver) {
        try {
            Map<String, Object> stats = statisticsService.getStorageStatistics();

            StorageStatsResponse.Builder builder = StorageStatsResponse.newBuilder();

            if (stats.containsKey("total_storage_used"))
                builder.setTotalStorageUsed((Long) stats.get("total_storage_used"));
            if (stats.containsKey("total_storage_allocated"))
                builder.setTotalStorageAllocated((Long) stats.get("total_storage_allocated"));

            List<Object[]> planStats = (List<Object[]>) stats.get("storage_by_plan");
            if (planStats != null) {
                for (Object[] row : planStats) {
                    builder.addStorageByPlan(StorageByPlan.newBuilder()
                            .setPlan((String) row[0])
                            .setUsersCount(((Number) row[1]).intValue())
                            .setStorageUsed(((Number) row[2]).longValue())
                            .setStorageAllocated(((Number) row[3]).longValue())
                            .build());
                }
            }

            List<User> topUsers = (List<User>) stats.get("top_users");
            if (topUsers != null) {
                for (User user : topUsers) {
                    builder.addTopUsers(TopStorageUser.newBuilder()
                            .setUserId(user.getId().toString())
                            .setEmail(user.getEmail())
                            .setName(user.getName() != null ? user.getName() : "")
                            .setStorageUsed(user.getStorageUsed())
                            .build());
                }
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting storage statistics", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get storage statistics: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getUserStatistics(GetUserStatisticsRequest request,
            StreamObserver<UserStatsResponse> responseObserver) {
        try {
            Map<String, Object> stats = statisticsService.getUserStatistics();

            UserStatsResponse.Builder builder = UserStatsResponse.newBuilder();
            if (stats.containsKey("total_users"))
                builder.setTotalUsers((Integer) stats.get("total_users"));

            List<Object[]> dateStats = (List<Object[]>) stats.get("users_by_date");
            if (dateStats != null) {
                for (Object[] row : dateStats) {
                    builder.addUsersByDate(UsersByDate.newBuilder()
                            .setDate(row[0].toString())
                            .setCount(((Number) row[1]).intValue())
                            .build());
                }
            }

            if (stats.containsKey("new_users_last_24h"))
                builder.setNewUsersLast24H((Integer) stats.get("new_users_last_24h"));
            if (stats.containsKey("active_users_last_hour"))
                builder.setActiveUsersLastHour((Integer) stats.get("active_users_last_hour"));
            if (stats.containsKey("blocked_users"))
                builder.setBlockedUsers((Integer) stats.get("blocked_users"));
            if (stats.containsKey("admin_count"))
                builder.setAdminCount((Integer) stats.get("admin_count"));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting user statistics", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get user statistics: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getActiveUsers(GetActiveUsersRequest request,
            StreamObserver<ActiveUsersResponse> responseObserver) {
        try {
            int minutes = request.getMinutes() > 0 ? request.getMinutes() : 60;
            Map<String, Object> result = statisticsService.getActiveUsers(minutes);

            ActiveUsersResponse.Builder builder = ActiveUsersResponse.newBuilder();
            int count = result.containsKey("count") ? (Integer) result.get("count") : 0;
            builder.setCount(count);

            List<String> userIds = (List<String>) result.get("user_ids");
            if (userIds != null && !userIds.isEmpty()) {
                List<UUID> uuids = userIds.stream().map(UUID::fromString).collect(Collectors.toList());
                List<User> users = userService.getUsersByIds(uuids);

                for (User user : users) {
                    builder.addUsers(ActiveUserInfo.newBuilder()
                            .setUserId(user.getId().toString())
                            .setEmail(user.getEmail())
                            .setName(user.getName() != null ? user.getName() : "")
                            .setLastActive(user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : "")
                            .build());
                }
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting active users", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get active users: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
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

    @Override
    public void blockUser(BlockUserRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            log.info("Blocking user: {} by admin: {}", request.getUserId(), request.getAdminId());
            userService.blockUser(
                    UUID.fromString(request.getAdminId()),
                    UUID.fromString(request.getUserId()),
                    request.getReason());
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error blocking user {}: {}", request.getUserId(), e.getMessage(), e);
            responseObserver.onError(
                    io.grpc.Status.INTERNAL
                            .withDescription("Failed to block user: " + e.getMessage())
                            .withCause(e)
                            .asRuntimeException());
        }
    }

    @Override
    public void unblockUser(UnblockUserRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            userService.unblockUser(
                    UUID.fromString(request.getAdminId()),
                    UUID.fromString(request.getUserId()));
            responseObserver.onNext(EmptyResponse.newBuilder().build());
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
                .setStorageUsed(user.getStorageUsed() != null ? user.getStorageUsed() : 0L)
                .setStorageQuota(user.getStorageQuota() != null ? user.getStorageQuota() : 0L)
                .setIsBlocked(user.getIsBlocked() != null ? user.getIsBlocked() : false)
                .setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                // .setLastLoginAt() // Needed from Auth
                .build();
    }
}
