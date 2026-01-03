package com.fileservice.repository;


import com.fileservice.model.FileShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileShareRepository extends JpaRepository<FileShare, UUID> {

    Optional<FileShare> findByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    List<FileShare> findByFileId(UUID fileId);

    @Query("SELECT fs FROM FileShare fs WHERE fs.file.id = :fileId " +
            "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now)")
    List<FileShare> findActiveSharesByFileId(@Param("fileId") UUID fileId,
                                             @Param("now") LocalDateTime now);

    @Query("SELECT fs FROM FileShare fs WHERE fs.sharedWithUserId = :userId " +
            "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now)")
    Page<FileShare> findActiveSharesForUser(@Param("userId") UUID userId,
                                            @Param("now") LocalDateTime now,
                                            Pageable pageable);

    Page<FileShare> findByOwnerId(UUID ownerId, Pageable pageable);

    @Query("SELECT COUNT(fs) FROM FileShare fs WHERE fs.file.id = :fileId " +
            "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now)")
    long countActiveShares(@Param("fileId") UUID fileId, @Param("now") LocalDateTime now);

    @Query("SELECT CASE WHEN COUNT(fs) > 0 THEN true ELSE false END " +
            "FROM FileShare fs WHERE fs.file.id = :fileId " +
            "AND fs.sharedWithUserId = :userId " +
            "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now)")
    boolean existsActiveShare(@Param("fileId") UUID fileId,
                              @Param("userId") UUID userId,
                              @Param("now") LocalDateTime now);

    @Query("SELECT fs FROM FileShare fs WHERE fs.expiresAt IS NOT NULL " +
            "AND fs.expiresAt < :now")
    List<FileShare> findExpiredShares(@Param("now") LocalDateTime now);
}
