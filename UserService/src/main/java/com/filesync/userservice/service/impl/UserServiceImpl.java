package com.filesync.userservice.service.impl;

import com.filesync.userservice.model.domain.AdminAction;
import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.model.domain.UserSettings;
import com.filesync.userservice.repository.AdminActionRepository;
import com.filesync.userservice.repository.UserQuotaRepository;
import com.filesync.userservice.repository.UserRepository;
import com.filesync.userservice.repository.UserSettingsRepository;
import com.filesync.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AdminActionRepository adminActionRepository;
    private final com.filesync.userservice.service.integration.AuthServiceClient authServiceClient;
    private final com.filesync.userservice.event.UserEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with email " + email + " not found"));
    }

    @Override
    @Transactional
    public User createUser(UUID userId, String email, String name) {
        // Проверяем, существует ли пользователь (например, создан через fallback в
        // checkQuota)
        User user = userRepository.findById(userId).orElse(null);

        if (user != null) {
            String placeholderEmail = "user-" + userId + "@example.com";
            boolean isInputPlaceholder = email.equals(placeholderEmail);
            boolean isCurrentReal = !user.getEmail().equals(placeholderEmail);

            if (isInputPlaceholder && isCurrentReal) {
                log.info("Skipping update of user {} with placeholder data", userId);
            } else {
                log.info("Updating existing user entity with registration data: {}", userId);
                // Пользователь уже есть, обновляем данные, полученные при регистрации (они
                // точнее, чем fallback)
                user.setEmail(email);
                user.setName(name != null ? name : "User");
                // Остальные поля не трогаем, чтобы не сбросить статистику
            }
        } else {
            log.info("Creating new user entity: {}", userId);
            user = User.builder()
                    .id(userId)
                    .email(email)
                    .name(name != null ? name : "User")
                    .storageUsed(0L)
                    .storageQuota(5368709120L) // 5GB default
                    .build();
        }

        user = userRepository.saveAndFlush(user);

        // Гарантируем наличие квоты
        if (userQuotaRepository.findByUserId(userId).isEmpty()) {
            log.info("Manually creating quota for user: {}", userId);
            userQuotaRepository.save(UserQuota.builder()
                    .user(user)
                    .planType("free")
                    .maxFileSize(104857600L) // 100MB
                    .maxDevices(3)
                    .maxShares(10)
                    .versionHistoryDays(30)
                    .validFrom(java.time.LocalDateTime.now())
                    .build());
        }

        // Гарантируем наличие настроек
        if (userSettingsRepository.findByUserId(userId).isEmpty()) {
            log.info("Manually creating settings for user: {}", userId);
            userSettingsRepository.save(UserSettings.builder()
                    .user(user)
                    .theme("light")
                    .language("en")
                    .notificationsEnabled(true)
                    .emailNotifications(true)
                    .autoSync(true)
                    .syncOnMobileData(false)
                    .build());
        }

        return user;
    }

    @Override
    @Transactional
    public User updateUser(UUID userId, String name, String avatarUrl) {
        User user = getUser(userId);
        if (name != null)
            user.setName(name);
        if (avatarUrl != null)
            user.setAvatarUrl(avatarUrl);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        log.info("Starting deletion of user: {}", userId);

        // 1. Call Auth Service to delete credentials and roles
        // We do this BEFORE local deletion if we want to ensure we can reach Auth,
        // OR AFTER if we value local consistency more.
        // Given bidirectional sync, it's safer to attempt Auth first, or at least
        // concurrently.
        // If Auth delete fails, we probably should abort to avoid inconsistency
        // (phantom user in Auth)
        try {
            authServiceClient.deleteUser(userId);
        } catch (Exception e) {
            log.error("Failed to delete user in Auth Service: {}", e.getMessage());
            throw new RuntimeException("Failed to delete user in Auth Service", e);
        }

        // 2. Perform local deletion
        deleteUserLocal(userId);

        log.info("User deleted successfully (initiated by User Service): {}", userId);
    }

    @Override
    @Transactional
    public void syncDeleteUser(UUID userId) {
        log.info("Syncing deletion of user from Auth Service: {}", userId);
        // Only delete local data, do not call Auth Service back
        deleteUserLocal(userId);
        log.info("User synced deletion successfully: {}", userId);
    }

    private void deleteUserLocal(UUID userId) {
        // 1. Delete admin actions where user is target
        adminActionRepository.deleteByTargetUserId(userId);

        // 2. Delete admin actions where user was admin (if any)
        adminActionRepository.deleteByAdminId(userId);

        // 3. Delete user entity (Cascade should handle settings and quotas if DB
        // configured)
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
        } else {
            log.warn("User {} not found for local deletion (might be already deleted)", userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserSettings getUserSettings(UUID userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Settings not found for user: " + userId));
    }

    @Override
    @Transactional
    public UserSettings updateUserSettings(UUID userId, UserSettings newSettings) {
        UserSettings current = getUserSettings(userId);

        if (newSettings.getTheme() != null)
            current.setTheme(newSettings.getTheme());
        if (newSettings.getLanguage() != null)
            current.setLanguage(newSettings.getLanguage());
        if (newSettings.getNotificationsEnabled() != null)
            current.setNotificationsEnabled(newSettings.getNotificationsEnabled());
        if (newSettings.getEmailNotifications() != null)
            current.setEmailNotifications(newSettings.getEmailNotifications());
        if (newSettings.getAutoSync() != null)
            current.setAutoSync(newSettings.getAutoSync());
        if (newSettings.getSyncOnMobileData() != null)
            current.setSyncOnMobileData(newSettings.getSyncOnMobileData());

        return userSettingsRepository.save(current);
    }

    @Override
    @Transactional(readOnly = true)
    public UserQuota checkQuota(UUID userId, long fileSize) {
        log.debug("Checking quota for user {} with file size {}", userId, fileSize);

        UserQuota quota = userQuotaRepository.findByUserId(userId)
                .orElse(null);

        if (quota == null) {
            log.warn("Quota not found for user {}, this should have been created by trigger or createUser", userId);
            throw new RuntimeException("Quota not found for user: " + userId);
        }

        // Проверяем, что у пользователя есть запись в таблице users
        User user = quota.getUser();
        if (user == null) {
            log.warn("User record not found for quota userId={}", userId);
            throw new RuntimeException("User record not found for userId: " + userId);
        }

        // Предотвращаем LazyInitializationException путем обращения к свойствам user
        log.debug("Quota found for user {}: plan={}, used={}, quota={}",
                userId, quota.getPlanType(), user.getStorageUsed(), user.getStorageQuota());

        return quota;
    }

    @Override
    @Transactional
    public void updateStorageUsed(UUID userId, long delta) {
        User user = getUser(userId);
        long newUsed = user.getStorageUsed() + delta;
        if (newUsed < 0)
            newUsed = 0; // Prevent negative
        user.setStorageUsed(newUsed);
        userRepository.save(user);
    }

    @Override
    public Page<User> listUsers(String search, String plan, Pageable pageable) {
        String searchParam = (search != null && !search.isEmpty()) ? "%" + search.toLowerCase() + "%" : null;
        return userRepository.findAllWithFilters(searchParam, plan, pageable);
    }

    @Override
    @Transactional
    public void updateUserQuota(UUID adminId, UUID userId, long newQuota) {
        User user = getUser(userId);
        user.setStorageQuota(newQuota);
        userRepository.save(user);

        logAdminAction(adminId, "UPDATE_QUOTA", userId, "New quota: " + newQuota);
    }

    @Override
    @Transactional
    public void changeUserPlan(UUID adminId, UUID userId, String newPlan) {
        UserQuota quota = userQuotaRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Quota not found"));

        quota.setPlanType(newPlan);
        // Update defaults based on plan type here usually...
        userQuotaRepository.save(quota);

        logAdminAction(adminId, "CHANGE_PLAN", userId, "New plan: " + newPlan);
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

    @Override
    public java.util.List<User> getUsersByIds(java.util.List<UUID> ids) {
        return userRepository.findAllById(ids);
    }

    @Override
    @Transactional
    public void blockUser(UUID adminId, UUID userId, String reason) {
        // 1. Update LOCAL status FIRST (source of truth)
        blockUserLocal(userId, reason, adminId);

        // 2. Log action (don't fail if logging fails)
        try {
            logAdminAction(adminId, "BLOCK_USER", userId, "Reason: " + reason);
        } catch (Exception e) {
            log.error("Failed to log admin action, but user blocked: {}", e.getMessage());
        }

        // 3. Update Auth Service AFTER (don't fail if it fails)
        try {
            authServiceClient.blockUser(userId, reason);
        } catch (Exception e) {
            log.error("Failed to block user in AuthService, but local DB updated: {}", e.getMessage());
            // Don't rethrow - local DB is already updated
            // AuthService will sync via Kafka later if needed
        }

        // 4. Publish user.blocked event for notifications
        publishUserBlockedEvent(userId, reason, adminId);
    }

    @Override
    @Transactional
    public void syncBlockUser(UUID userId, String reason) {
        log.info("Syncing block of user from Auth Service: {}", userId);
        blockUserLocal(userId, reason, null); // No admin info in sync event usually, or put null
    }

    private void blockUserLocal(UUID userId, String reason, UUID adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setIsBlocked(true);
        user.setBlockedReason(reason);
        user.setBlockedAt(LocalDateTime.now());
        user.setBlockedBy(adminId);

        userRepository.saveAndFlush(user);
    }

    private void publishUserBlockedEvent(UUID userId, String reason, UUID adminId) {
        try {
            eventPublisher.publish(com.filesync.userservice.event.UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("user.blocked")
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .metadata(java.util.Map.of(
                            "reason", reason != null ? reason : "",
                            "blockedBy", adminId != null ? adminId.toString() : "system"))
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish user.blocked event: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void unblockUser(UUID adminId, UUID userId) {
        // 1. Update LOCAL status FIRST (source of truth)
        unblockUserLocal(userId);

        // 2. Log action (don't fail if logging fails)
        try {
            logAdminAction(adminId, "UNBLOCK_USER", userId, "Unblocked");
        } catch (Exception e) {
            log.error("Failed to log admin action, but user unblocked: {}", e.getMessage());
        }

        // 3. Update Auth Service AFTER (don't fail if it fails)
        try {
            authServiceClient.unblockUser(userId);
        } catch (Exception e) {
            log.error("Failed to unblock user in AuthService, but local DB updated: {}", e.getMessage());
        }

        // 4. Publish user.unblocked event
        publishUserUnblockedEvent(userId, adminId);
    }

    @Override
    @Transactional
    public void syncUnblockUser(UUID userId) {
        log.info("Syncing unblock of user from Auth Service: {}", userId);
        unblockUserLocal(userId);
    }

    private void unblockUserLocal(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setIsBlocked(false);
        user.setBlockedReason(null);
        user.setBlockedAt(null);
        user.setBlockedBy(null);

        userRepository.saveAndFlush(user);
    }

    private void publishUserUnblockedEvent(UUID userId, UUID adminId) {
        try {
            eventPublisher.publish(com.filesync.userservice.event.UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("user.unblocked")
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .metadata(java.util.Map.of("unblockedBy", adminId != null ? adminId.toString() : "system"))
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish user.unblocked event: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateLastLogin(UUID userId, java.time.LocalDateTime lastLoginAt) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setLastLoginAt(lastLoginAt);
            userRepository.save(user);
        }
        // If user not found, maybe just created in Auth but not here?
        // Kafka consumer handles USER_REGISTERED so it should exist.
        // If race condition, we might miss it, but acceptable for last login stats.
    }
}
