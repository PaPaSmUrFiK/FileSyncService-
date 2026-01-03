package com.authservice.service;


import com.authservice.service.dto.TokenPairDto;

import java.util.Set;
import java.util.UUID;

public interface AuthService {

    TokenPairDto register(String email, String password, String name);

    TokenPairDto login(String email, String password, String deviceInfo);

    TokenPairDto refresh(String refreshToken);

    void validate(String accessToken);

    void logout(String refreshToken);

    void logoutAll(UUID userId);

    void assignRole(UUID userId, String roleName);

    void revokeRole(UUID userId, String roleName);

    Set<String> getUserRoles(UUID userId);
}
