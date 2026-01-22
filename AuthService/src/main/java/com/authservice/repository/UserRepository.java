package com.authservice.repository;

import com.authservice.model.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
        boolean existsByEmail(String email);

        Optional<User> findByEmail(String email);

        /**
         * Используется Security / Auth
         * roles подгружаются сразу
         */
        @EntityGraph(attributePaths = "roles")
        Optional<User> findWithRolesByEmail(String email);

        /**
         * Находит пользователя с ролями по ID
         */
        @EntityGraph(attributePaths = "roles")
        Optional<User> findWithRolesById(UUID id);

        @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.lastLoginAt BETWEEN :from AND :to")
        int countActiveUsers(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                        @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

        @org.springframework.data.jpa.repository.Query("SELECT u.id FROM User u WHERE u.lastLoginAt >= :threshold")
        java.util.List<UUID> findActiveUserIds(
                        @org.springframework.data.repository.query.Param("threshold") java.time.LocalDateTime threshold);

        long countByRoles_NameIn(java.util.Collection<String> roleNames);

        long countByIsBlocked(Boolean isBlocked);
}
