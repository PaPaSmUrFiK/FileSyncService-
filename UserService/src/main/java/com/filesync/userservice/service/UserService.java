package com.filesync.userservice.service;

import com.filesync.userservice.model.domain.User;
import com.filesync.userservice.model.domain.UserQuota;
import com.filesync.userservice.model.domain.UserSettings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    User getUser(UUID userId);

    User createUser(UUID userId, String email, String name);

    User updateUser(UUID userId, String name, String avatarUrl);

    void deleteUser(UUID userId);

    UserSettings getUserSettings(UUID userId);

    UserSettings updateUserSettings(UUID userId, UserSettings newSettings);

    UserQuota checkQuota(UUID userId, long fileSize);

    void updateStorageUsed(UUID userId, long delta);

    // Admin
    Page<User> listUsers(String search, String plan, Pageable pageable);

    void updateUserQuota(UUID adminId, UUID userId, long newQuota);

    void changeUserPlan(UUID adminId, UUID userId, String newPlan);
}
