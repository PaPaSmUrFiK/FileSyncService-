package com.filesync.userservice.service.impl;

import com.filesync.userservice.model.domain.AdminAction;
import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.repository.AdminActionRepository;
import com.filesync.userservice.repository.UserQuotaRepository;
import com.filesync.userservice.repository.UserRepository;
import com.filesync.userservice.service.AdminService;
import com.filesync.userservice.service.integration.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Реализация административных функций
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AdminActionRepository adminActionRepository;
    private final AuthServiceClient authServiceClient;
    private final com.filesync.userservice.event.UserEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<User> listUsers(String search, String plan, Pageable pageable) {
        log.debug("Listing users: search={}, plan={}, page={}", search, plan, pageable.getPageNumber());
        Page<User> users = userRepository.findAllWithFilters(search, plan, pageable);

        // Fetch roles from AuthService for each user
        users.forEach(user -> {
            try {
                List<String> userRoles = authServiceClient.getUserRoles(user.getId());
                user.setRoles(userRoles);
                log.debug("Fetched roles for user {}: {}", user.getEmail(), userRoles);
            } catch (Exception e) {
                log.warn("Failed to fetch roles for user {}: {}", user.getId(), e.getMessage());
                // Set empty list if failed to fetch
                user.setRoles(java.util.Collections.emptyList());
            }
        });

        return users;
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserDetails(UUID userId) {
        log.debug("Getting user details: userId={}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    @Override
    @Transactional
    public void updateUserQuota(UUID adminId, UUID userId, long newQuota) {
        log.info("Updating user quota: adminId={}, userId={}, newQuota={}", adminId, userId, newQuota);

        User user = getUserDetails(userId);
        user.setStorageQuota(newQuota);
        userRepository.save(user);

        logAdminAction(adminId, "UPDATE_QUOTA", userId,
                String.format("{\"old_quota\": %d, \"new_quota\": %d}", user.getStorageQuota(), newQuota));
    }

    @Override
    @Transactional
    public void changeUserPlan(UUID adminId, UUID userId, String newPlan) {
        log.info("Changing user plan: adminId={}, userId={}, newPlan={}", adminId, userId, newPlan);

        // Validate plan
        if (!List.of("free", "premium", "business", "enterprise").contains(newPlan.toLowerCase())) {
            throw new IllegalArgumentException("Invalid plan: " + newPlan);
        }

        User user = getUserDetails(userId);
        String oldPlan = user.getPlan();
        user.setPlan(newPlan);

        // Update quota based on plan
        UserQuota quota = userQuotaRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Quota not found for user: " + userId));

        quota.setPlanType(newPlan);

        // Set plan-specific limits
        switch (newPlan.toLowerCase()) {
            case "free":
                user.setStorageQuota(5368709120L); // 5GB
                user.setMaxFileSize(104857600L); // 100MB
                user.setMaxDevices(3);
                user.setMaxShares(10);
                quota.setMaxFileSize(104857600L);
                quota.setMaxDevices(3);
                quota.setMaxShares(10);
                quota.setVersionHistoryDays(30);
                break;
            case "premium":
                user.setStorageQuota(107374182400L); // 100GB
                user.setMaxFileSize(1073741824L); // 1GB
                user.setMaxDevices(10);
                user.setMaxShares(100);
                quota.setMaxFileSize(1073741824L);
                quota.setMaxDevices(10);
                quota.setMaxShares(100);
                quota.setVersionHistoryDays(90);
                break;
            case "business":
                user.setStorageQuota(1099511627776L); // 1TB
                user.setMaxFileSize(5368709120L); // 5GB
                user.setMaxDevices(50);
                user.setMaxShares(1000);
                quota.setMaxFileSize(5368709120L);
                quota.setMaxDevices(50);
                quota.setMaxShares(1000);
                quota.setVersionHistoryDays(365);
                break;
            case "enterprise":
                user.setStorageQuota(10995116277760L); // 10TB
                user.setMaxFileSize(10737418240L); // 10GB
                user.setMaxDevices(Integer.MAX_VALUE);
                user.setMaxShares(Integer.MAX_VALUE);
                quota.setMaxFileSize(10737418240L);
                quota.setMaxDevices(Integer.MAX_VALUE);
                quota.setMaxShares(Integer.MAX_VALUE);
                quota.setVersionHistoryDays(Integer.MAX_VALUE);
                break;
        }

        userRepository.save(user);
        userQuotaRepository.save(quota);

        logAdminAction(adminId, "CHANGE_PLAN", userId,
                String.format("{\"old_plan\": \"%s\", \"new_plan\": \"%s\"}", oldPlan, newPlan));
    }

    @Override
    @Transactional
    public void blockUser(UUID adminId, UUID userId, String reason) {
        log.info("Blocking user: adminId={}, userId={}, reason={}", adminId, userId, reason);

        // Delegate to AuthService (source of truth for blocking)
        authServiceClient.blockUser(userId, reason);

        // Update local user record
        User user = getUserDetails(userId);
        user.setIsBlocked(true);
        user.setBlockedReason(reason);
        user.setBlockedAt(LocalDateTime.now());
        user.setBlockedBy(adminId);
        userRepository.save(user);

        logAdminAction(adminId, "BLOCK_USER", userId,
                String.format("{\"reason\": \"%s\"}", reason));
    }

    @Override
    @Transactional
    public void unblockUser(UUID adminId, UUID userId) {
        log.info("Unblocking user: adminId={}, userId={}", adminId, userId);

        // Delegate to AuthService
        authServiceClient.unblockUser(userId);

        // Update local user record
        User user = getUserDetails(userId);
        user.setIsBlocked(false);
        user.setBlockedReason(null);
        user.setBlockedAt(null);
        user.setBlockedBy(null);
        userRepository.save(user);

        logAdminAction(adminId, "UNBLOCK_USER", userId, "{}");
    }

    @Override
    public void assignUserRole(UUID adminId, UUID userId, String roleName) {
        log.info("Assigning role: adminId={}, userId={}, role={}", adminId, userId, roleName);

        // Delegate to AuthService (source of truth for roles)
        authServiceClient.assignRole(userId, roleName);

        logAdminAction(adminId, "ASSIGN_ROLE", userId,
                String.format("{\"role\": \"%s\"}", roleName));

        // Publish user.role_changed event
        try {
            eventPublisher.publish(com.filesync.userservice.event.UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("user.role_changed")
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .metadata(java.util.Map.of(
                            "action", "assigned",
                            "role", roleName,
                            "changedBy", adminId.toString()))
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish user.role_changed event: {}", e.getMessage());
        }
    }

    @Override
    public void revokeUserRole(UUID adminId, UUID userId, String roleName) {
        log.info("Revoking role: adminId={}, userId={}, role={}", adminId, userId, roleName);

        // Delegate to AuthService
        authServiceClient.revokeRole(userId, roleName);

        logAdminAction(adminId, "REVOKE_ROLE", userId,
                String.format("{\"role\": \"%s\"}", roleName));

        // Publish user.role_changed event
        try {
            eventPublisher.publish(com.filesync.userservice.event.UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("user.role_changed")
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .metadata(java.util.Map.of(
                            "action", "revoked",
                            "role", roleName,
                            "changedBy", adminId.toString()))
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish user.role_changed event: {}", e.getMessage());
        }
    }

    @Override
    public List<String> getUserRoles(UUID userId) {
        log.debug("Getting user roles: userId={}", userId);
        // Delegate to AuthService
        return authServiceClient.getUserRoles(userId);
    }

    private void logAdminAction(UUID adminId, String actionType, UUID targetUserId, String details) {
        // Ensure details is valid JSON - wrap plain text in JSON object
        String jsonDetails = details;
        if (details != null && !details.trim().startsWith("{")) {
            // Simple text - wrap in JSON object
            jsonDetails = String.format("{\"message\": \"%s\"}",
                    details.replace("\"", "\\\"").replace("\n", "\\n"));
        }

        adminActionRepository.save(AdminAction.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .actionDetails(jsonDetails)
                .build());
    }
}
