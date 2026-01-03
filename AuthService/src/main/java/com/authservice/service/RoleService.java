package com.authservice.service;

import java.util.Set;
import java.util.UUID;

public interface RoleService {

    void assignRole(UUID userId, String roleName);

    void revokeRole(UUID userId, String roleName);

    Set<String> getUserRoles(UUID userId);
}
