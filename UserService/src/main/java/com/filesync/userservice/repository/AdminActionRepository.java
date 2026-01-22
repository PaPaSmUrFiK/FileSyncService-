package com.filesync.userservice.repository;

import com.filesync.userservice.model.domain.AdminAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {
    void deleteByTargetUserId(UUID targetUserId);

    void deleteByAdminId(UUID adminId);
}
