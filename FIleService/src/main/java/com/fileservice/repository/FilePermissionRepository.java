package com.fileservice.repository;


import com.fileservice.model.FilePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FilePermissionRepository extends JpaRepository<FilePermission, UUID> {

    List<FilePermission> findByFileIdAndUserId(UUID fileId, UUID userId);

    List<FilePermission> findByFileId(UUID fileId);

    @Query("SELECT CASE WHEN COUNT(fp) > 0 THEN true ELSE false END " +
            "FROM FilePermission fp WHERE fp.file.id = :fileId " +
            "AND fp.userId = :userId AND fp.permission = :permission")
    boolean hasPermission(@Param("fileId") UUID fileId,
                          @Param("userId") UUID userId,
                          @Param("permission") FilePermission.PermissionType permission);

    @Query("SELECT DISTINCT fp.file.id FROM FilePermission fp WHERE fp.userId = :userId")
    List<UUID> findAccessibleFileIds(@Param("userId") UUID userId);

    @Query(value = "SELECT * FROM file_permissions WHERE file_id = :fileId " +
            "AND user_id = :userId ORDER BY permission DESC LIMIT 1", nativeQuery = true)
    Optional<FilePermission> findHighestPermission(@Param("fileId") UUID fileId,
                                                   @Param("userId") UUID userId);

    void deleteByFileId(UUID fileId);

    long countByFileId(UUID fileId);
}
