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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AdminActionRepository adminActionRepository;

    @Override
    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    @Override
    @Transactional
    public User createUser(UUID userId, String email, String name) {
        User user = userRepository.findById(userId).orElseGet(() -> {
            User newUser = User.builder()
                    .id(userId)
                    .email(email)
                    .name(name != null ? name : "User")
                    .storageUsed(0L)
                    .storageQuota(5368709120L) // 5GB default
                    .build();

            log.info("Creating new user entity: {}", userId);
            return userRepository.saveAndFlush(newUser);
        });

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
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
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
        return userRepository.findAllWithFilters(search, plan, pageable);
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
        adminActionRepository.save(AdminAction.builder()
                .adminId(adminId)
                .actionType(actionType)
                .targetUserId(targetUserId)
                .actionDetails(details) // JSON string
                .build());
    }
}
