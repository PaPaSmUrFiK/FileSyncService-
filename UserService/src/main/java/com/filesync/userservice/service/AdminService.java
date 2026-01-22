package com.filesync.userservice.service;

import com.filesync.userservice.model.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Сервис для административных функций управления пользователями
 */
public interface AdminService {
    
    /**
     * Получить список пользователей с фильтрацией и пагинацией
     */
    Page<User> listUsers(String search, String plan, Pageable pageable);
    
    /**
     * Получить детальную информацию о пользователе
     */
    User getUserDetails(UUID userId);
    
    /**
     * Обновить квоту пользователя
     */
    void updateUserQuota(UUID adminId, UUID userId, long newQuota);
    
    /**
     * Изменить план пользователя
     */
    void changeUserPlan(UUID adminId, UUID userId, String newPlan);
    
    /**
     * Заблокировать пользователя (делегирует в AuthService)
     */
    void blockUser(UUID adminId, UUID userId, String reason);
    
    /**
     * Разблокировать пользователя (делегирует в AuthService)
     */
    void unblockUser(UUID adminId, UUID userId);
    
    /**
     * Назначить роль пользователю (делегирует в AuthService)
     */
    void assignUserRole(UUID adminId, UUID userId, String roleName);
    
    /**
     * Отозвать роль у пользователя (делегирует в AuthService)
     */
    void revokeUserRole(UUID adminId, UUID userId, String roleName);
    
    /**
     * Получить роли пользователя (делегирует в AuthService)
     */
    java.util.List<String> getUserRoles(UUID userId);
}
