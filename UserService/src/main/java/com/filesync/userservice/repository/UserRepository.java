package com.filesync.userservice.repository;

import com.filesync.userservice.model.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    // Dynamic stats queries

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt <= :date")
    long countUsersCreatedBefore(@Param("date") LocalDateTime date);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt BETWEEN :start AND :end")
    long countUsersRegisteredBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(u.storageUsed) FROM User u")
    Long getTotalStorageUsed();

    @Query("SELECT SUM(u.storageQuota) FROM User u")
    Long getTotalStorageAllocated();

    @Query("SELECT u FROM User u ORDER BY u.storageUsed DESC")
    List<User> findTopUsersByStorage(Pageable pageable);

    // Search for admin list
    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))) "
            +
            "AND (:plan IS NULL OR EXISTS (SELECT q FROM UserQuota q WHERE q.user = u AND q.planType = :plan))")
    Page<User> findAllWithFilters(@Param("search") String search, @Param("plan") String plan, Pageable pageable);

    @Query("SELECT DATE(u.createdAt) as date, COUNT(u) as count FROM User u WHERE u.createdAt >= :fromDate GROUP BY DATE(u.createdAt) ORDER BY date")
    List<Object[]> getRegistrationDynamics(@Param("fromDate") LocalDateTime fromDate);
}
