package com.authservice.service;

import com.authservice.exception.AuthException;
import com.authservice.exception.UserBlockedException;
import com.authservice.model.domain.Role;
import com.authservice.model.domain.User;
import com.authservice.repository.RoleRepository;
import com.authservice.repository.UserRepository;
import com.authservice.security.JwtTokenProvider;
import com.authservice.service.dto.TokenPairDto;
import com.authservice.service.messaging.KafkaProducerService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final KafkaProducerService kafkaProducerService;
    private final com.authservice.event.UserEventPublisher eventPublisher;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    /*
     * =========================
     * Register
     * =========================
     */

    @Override
    public TokenPairDto register(String email, String password, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("Email already registered");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Default role USER not found"));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name(name)
                .isBlocked(false)
                .roles(Set.of(userRole))
                .lastLoginAt(LocalDateTime.now()) // Set initial login time
                .build();

        userRepository.save(user);

        // Publish registration event for other services (e.g. UserService)
        kafkaProducerService.sendUserRegisteredEvent(user.getId(), user.getEmail(), user.getName());

        return issueTokens(user, null);
    }

    /*
     * =========================
     * Login
     * =========================
     */

    @Override
    public TokenPairDto login(String email, String password, String deviceInfo) {
        User user = userRepository.findWithRolesByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new UserBlockedException(user.getBlockedReason());
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user); // Validate we save the timestamp in Auth DB

        tokenService.revokeAllUserTokens(user.getId());

        // Notify other services
        kafkaProducerService.sendUserLoggedInEvent(user.getId());

        return issueTokens(user, deviceInfo);
    }

    /*
     * =========================
     * Refresh
     * =========================
     */

    @Override
    public TokenPairDto refresh(String refreshToken) {
        var storedToken = tokenService.validateRefreshToken(refreshToken);

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new AuthException("User not found"));

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new UserBlockedException(user.getBlockedReason());
        }

        tokenService.revokeRefreshToken(refreshToken);

        return issueTokens(user, storedToken.getDeviceInfo());
    }

    /*
     * =========================
     * Validate
     * =========================
     */

    @Override
    public void validate(String accessToken) {
        if (!jwtTokenProvider.validate(accessToken)) {
            throw new AuthException("Invalid access token");
        }
    }

    /*
     * =========================
     * Logout
     * =========================
     */

    @Override
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    /*
     * =========================
     * Logout all
     * =========================
     */

    @Override
    public void logoutAll(UUID userId) {
        tokenService.revokeAllUserTokens(userId);
    }

    /*
     * =========================
     * Role management
     * =========================
     */

    @Override
    public void assignRole(UUID userId, String roleName) {
        roleService.assignRole(userId, roleName);
    }

    @Override
    public void revokeRole(UUID userId, String roleName) {
        roleService.revokeRole(userId, roleName);
    }

    @Override
    public Set<String> getUserRoles(UUID userId) {
        return roleService.getUserRoles(userId);
    }

    /*
     * =========================
     * Internal helper
     * =========================
     */

    private TokenPairDto issueTokens(User user, String deviceInfo) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                roles);

        String refreshToken = tokenService.createRefreshToken(
                user.getId(),
                deviceInfo);

        return TokenPairDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .roles(roles)
                .expiresIn(accessTokenExpirationSeconds)
                .build();
    }

    /*
     * =========================
     * User blocking
     * =========================
     */

    @Override
    public void blockUser(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found: " + userId));

        user.setIsBlocked(true);
        user.setBlockedReason(reason);
        user.setBlockedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all tokens
        tokenService.revokeAllUserTokens(userId);

        // Publish event for UserService to sync
        kafkaProducerService.sendUserBlockedEvent(userId, reason);
    }

    @Override
    public void unblockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found: " + userId));

        user.setIsBlocked(false);
        user.setBlockedReason(null);
        user.setBlockedAt(null);
        userRepository.save(user);

        // Publish event for UserService to sync
        kafkaProducerService.sendUserUnblockedEvent(userId);
    }

    /*
     * =========================
     * Statistics
     * =========================
     */

    @Override
    public int getActiveUsersCount(long fromTimestamp, long toTimestamp) {
        LocalDateTime from = LocalDateTime.ofEpochSecond(fromTimestamp, 0, java.time.ZoneOffset.UTC);
        LocalDateTime to = LocalDateTime.ofEpochSecond(toTimestamp, 0, java.time.ZoneOffset.UTC);

        return userRepository.countActiveUsers(from, to);
    }

    @Override
    public java.util.List<String> getUsersActiveInLastMinutes(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        java.util.List<UUID> userIds = userRepository.findActiveUserIds(threshold);
        return userIds.stream().map(UUID::toString).collect(Collectors.toList());
    }

    @Override
    public int getAdminCount() {
        return (int) userRepository.countByRoles_NameIn(java.util.List.of("ROLE_ADMIN"));
    }

    @Override
    public int getBlockedUsersCount() {
        return (int) userRepository.countByIsBlocked(true);
    }

    /*
     * =========================
     * User deletion
     * =========================
     */

    @Override
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found: " + userId));

        // Revoke all tokens
        tokenService.revokeAllUserTokens(userId);

        // Delete user roles (if they are not cascaded by DB, though likely ManyToMany)
        // JPA usually handles ManyToMany link table deletion if configured, but let's
        // be safe
        user.getRoles().clear();
        userRepository.save(user); // Clear associations

        // Delete user
        userRepository.delete(user);

        // Publish deletion event
        kafkaProducerService.sendUserDeletedEvent(userId);
    }

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthException("Invalid old password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Optionally revoke all tokens to force re-login, or keep them valid.
        // For security, changing password usually implies revoking existing sessions.
        tokenService.revokeAllUserTokens(userId);

        // Publish user.password_changed event for notifications
        try {
            eventPublisher.publish(com.authservice.event.UserEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("user.password_changed")
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            // Log but don't fail - password was already changed successfully
        }
    }
}
