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
    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
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
    public UserQuota checkQuota(UUID userId, long fileSize) {
        // Just return the quota object, calling logic decides if check passes?
        // Or throw exception?
        // The prompt says "CheckQuota(Request) returns (QuotaResponse)" which has bool
        // has_space.
        return userQuotaRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Quota not found for user: " + userId));
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
