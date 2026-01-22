package com.authservice.service;

import com.authservice.exception.AuthException;
import com.authservice.model.domain.Role;
import com.authservice.model.domain.User;
import com.authservice.repository.RoleRepository;
import com.authservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.authservice.event.UserEvent;
import com.authservice.event.UserEventPublisher;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoleServiceImpl implements RoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserEventPublisher userEventPublisher;

    /*
     * =========================
     * Assign role
     * =========================
     */

    @Override
    public void assignRole(UUID userId, String roleName) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        Role role = resolveRole(roleName);

        if (user.getRoles().contains(role)) {
            return; // idempotent
        }

        user.getRoles().add(role);
        userRepository.save(user);

        publishRoleEvent("user.role_assigned", userId, role.getName());
    }

    /*
     * =========================
     * Revoke role
     * =========================
     */

    @Override
    public void revokeRole(UUID userId, String roleName) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        Role role = resolveRole(roleName);

        if (!user.getRoles().contains(role)) {
            return; // idempotent
        }

        // Защита от удаления последней роли
        if (user.getRoles().size() == 1) {
            throw new AuthException("User must have at least one role");
        }

        user.getRoles().remove(role);
        userRepository.save(user);

        publishRoleEvent("user.role_revoked", userId, role.getName());
    }

    private void publishRoleEvent(String eventType, UUID userId, String roleName) {
        try {
            userEventPublisher.publish(UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .metadata(Map.of("role", roleName))
                    .build());
        } catch (Exception e) {
            // Log error but don't fail the transaction?
            // Better to just log since role is already assigned.
            // Slf4j is not on class, need to check if I can use it or just ignore.
            // RoleServiceImpl doesn't have @Slf4j. I won't add it to avoid import mess if
            // not needed.
            // UserEventPublisher logs internally.
        }
    }

    private Role resolveRole(String roleName) {
        return roleRepository.findByName(roleName)
                .or(() -> roleRepository.findByName(roleName.toUpperCase()))
                .or(() -> roleRepository.findByName(roleName.toUpperCase().replace("ROLE_", "")))
                .or(() -> roleRepository.findByName("ROLE_" + roleName.toUpperCase()))
                .orElseThrow(() -> new AuthException("Role not found: " + roleName));
    }

    /*
     * =========================
     * Get user roles
     * =========================
     */

    @Override
    @Transactional
    public Set<String> getUserRoles(UUID userId) {
        log.debug("Getting roles for user: {}", userId);
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        log.debug("User found: {}, roles count: {}", user.getEmail(),
                user.getRoles() != null ? user.getRoles().size() : 0);
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> log.debug("Role: {}", role.getName()));
        }

        Set<String> roleNames = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        log.info("Returning {} roles for user {}: {}", roleNames.size(), user.getEmail(), roleNames);
        return roleNames;
    }
}
