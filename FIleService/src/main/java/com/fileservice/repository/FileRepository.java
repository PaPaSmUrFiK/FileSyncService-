package com.fileservice.repository;

import com.fileservice.model.File;
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
public interface FileRepository extends JpaRepository<File, UUID> {

        Optional<File> findByIdAndUserId(UUID id, UUID userId);

        Optional<File> findByPathAndUserIdAndIsDeletedFalse(String path, UUID userId);

        Page<File> findByUserIdAndIsDeletedFalse(UUID userId, Pageable pageable);

        Page<File> findByParentFolderIdAndIsDeletedFalse(UUID parentFolderId, Pageable pageable);

        Page<File> findByUserIdAndParentFolderIdAndIsDeletedFalse(
                        UUID userId, UUID parentFolderId, Pageable pageable);

        Page<File> findByUserIdAndParentFolderIdIsNullAndIsDeletedFalse(
                        UUID userId, Pageable pageable);

        List<File> findByHashAndIsDeletedFalse(String hash);

        Optional<File> findByHashAndUserIdAndIsDeletedFalse(String hash, UUID userId);

        @Query("SELECT f FROM File f WHERE f.userId = :userId AND f.isDeleted = false " +
                        "AND LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%'))")
        Page<File> searchByName(@Param("userId") UUID userId,
                        @Param("query") String query,
                        Pageable pageable);

        List<File> findByUserIdAndIsFolderTrueAndIsDeletedFalse(UUID userId);

        @Query("SELECT COALESCE(SUM(f.size), 0) FROM File f " +
                        "WHERE f.userId = :userId AND f.isDeleted = false AND f.isFolder = false")
        Long calculateStorageUsed(@Param("userId") UUID userId);

        long countByUserIdAndIsDeletedFalse(UUID userId);

        Page<File> findByUserIdAndIsDeletedTrue(UUID userId, Pageable pageable);

        List<File> findAllByUserIdAndIsDeletedTrue(UUID userId);

        boolean existsByPathAndUserIdAndIsDeletedFalse(String path, UUID userId);

        @Query("SELECT f FROM File f WHERE f.isDeleted = true AND f.deletedAt < :beforeDate")
        List<File> findFilesForCleanup(@Param("beforeDate") LocalDateTime beforeDate);

        @Query("SELECT f FROM File f WHERE f.userId = :userId AND f.isDeleted = false " +
                        "AND f.path LIKE CONCAT(:folderPath, '%')")
        List<File> findAllChildrenByPath(@Param("userId") UUID userId,
                        @Param("folderPath") String folderPath);
}
