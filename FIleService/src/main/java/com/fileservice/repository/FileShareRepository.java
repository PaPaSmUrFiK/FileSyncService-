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

        // Query to get share IDs with pagination (no JOIN FETCH to avoid count query
        // issues)
        @Query("SELECT fs.id FROM FileShare fs JOIN fs.file f WHERE fs.sharedWithUserId = :userId " +
                        "AND (fs.expiresAt IS NULL OR fs.expiresAt > :now) " +
                        "AND f.isDeleted = false")
        Page<UUID> findActiveShareIdsForUser(@Param("userId") UUID userId,
                        @Param("now") LocalDateTime now,
                        Pageable pageable);

        // Query to fetch full shares with files by IDs
        @Query("SELECT fs FROM FileShare fs JOIN FETCH fs.file WHERE fs.id IN :shareIds")
        List<FileShare> findByIdInWithFile(@Param("shareIds") List<UUID> shareIds);

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

        /**
         * Найти все shares для списка файлов
         */
        List<FileShare> findByFileIdIn(List<UUID> fileIds);
}
