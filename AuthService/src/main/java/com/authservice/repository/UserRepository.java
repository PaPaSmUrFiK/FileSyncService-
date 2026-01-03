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
}

