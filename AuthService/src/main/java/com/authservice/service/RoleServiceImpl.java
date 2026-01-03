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

@Service
@RequiredArgsConstructor
@Transactional
public class RoleServiceImpl implements RoleService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /* =========================
       Assign role
       ========================= */

    @Override
    public void assignRole(UUID userId, String roleName) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AuthException("Role not found"));

        if (user.getRoles().contains(role)) {
            return; // idempotent
        }

        user.getRoles().add(role);
        userRepository.save(user);
    }

    /* =========================
       Revoke role
       ========================= */

    @Override
    public void revokeRole(UUID userId, String roleName) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AuthException("Role not found"));

        if (!user.getRoles().contains(role)) {
            return; // idempotent
        }

        // Защита от удаления последней роли
        if (user.getRoles().size() == 1) {
            throw new AuthException("User must have at least one role");
        }

        user.getRoles().remove(role);
        userRepository.save(user);
    }

    /* =========================
       Get user roles
       ========================= */

    @Override
    @Transactional
    public Set<String> getUserRoles(UUID userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new AuthException("User not found"));

        return user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}

