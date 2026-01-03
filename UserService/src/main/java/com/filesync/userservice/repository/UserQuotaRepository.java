package com.filesync.userservice.repository;

import com.filesync.userservice.model.domain.UserQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuotaRepository extends JpaRepository<UserQuota, UUID> {
    Optional<UserQuota> findByUserId(UUID userId);

    @Query("SELECT q.planType, COUNT(q) FROM UserQuota q GROUP BY q.planType")
    List<Object[]> countUsersByPlan();

    @Query("SELECT q.planType, COUNT(q), SUM(u.storageUsed), SUM(u.storageQuota) " +
            "FROM UserQuota q JOIN q.user u " +
            "GROUP BY q.planType")
    List<Object[]> getStorageStatsByPlan();
}
