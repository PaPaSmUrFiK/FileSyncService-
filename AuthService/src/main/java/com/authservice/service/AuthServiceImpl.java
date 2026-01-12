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

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationSeconds;

    /* =========================
       Register
       ========================= */

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
                .build();

        userRepository.save(user);

        // Publish registration event for other services (e.g. UserService)
        kafkaProducerService.sendUserRegisteredEvent(user.getId(), user.getEmail(), user.getName());

        return issueTokens(user, null);
    }

    /* =========================
       Login
       ========================= */

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

        tokenService.revokeAllUserTokens(user.getId());

        return issueTokens(user, deviceInfo);
    }

    /* =========================
       Refresh
       ========================= */

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

    /* =========================
       Validate
       ========================= */

    @Override
    public void validate(String accessToken) {
        if (!jwtTokenProvider.validate(accessToken)) {
            throw new AuthException("Invalid access token");
        }
    }

    /* =========================
       Logout
       ========================= */

    @Override
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
    }

    /* =========================
       Logout all
       ========================= */

    @Override
    public void logoutAll(UUID userId) {
        tokenService.revokeAllUserTokens(userId);
    }

    /* =========================
       Role management
       ========================= */

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

    /* =========================
       Internal helper
       ========================= */

    private TokenPairDto issueTokens(User user, String deviceInfo) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                roles
        );

        String refreshToken = tokenService.createRefreshToken(
                user.getId(),
                deviceInfo
        );

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
}
