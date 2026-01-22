package com.filesync.userservice.service;

import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.model.domain.UserSettings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    User getUser(UUID userId);

    User getUserByEmail(String email);

    User createUser(UUID userId, String email, String name);

    User updateUser(UUID userId, String name, String avatarUrl);

    void deleteUser(UUID userId);

    void syncDeleteUser(UUID userId);

    UserSettings getUserSettings(UUID userId);

    UserSettings updateUserSettings(UUID userId, UserSettings newSettings);

    UserQuota checkQuota(UUID userId, long fileSize);

    void updateStorageUsed(UUID userId, long delta);

    // Admin
    Page<User> listUsers(String search, String plan, Pageable pageable);

    void updateUserQuota(UUID adminId, UUID userId, long newQuota);

    void changeUserPlan(UUID adminId, UUID userId, String newPlan);

    java.util.List<User> getUsersByIds(java.util.List<UUID> ids);

    void blockUser(UUID adminId, UUID userId, String reason);

    void syncBlockUser(UUID userId, String reason);

    void unblockUser(UUID adminId, UUID userId);

    void syncUnblockUser(UUID userId);

    void updateLastLogin(UUID userId, java.time.LocalDateTime lastLoginAt);
}
