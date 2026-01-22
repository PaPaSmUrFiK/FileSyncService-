package com.fileservice.repository;

import com.fileservice.model.FileVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

        List<FileVersion> findByFileIdOrderByVersionDesc(UUID fileId);

        Page<FileVersion> findByFileIdOrderByVersionDesc(UUID fileId, Pageable pageable);

        Optional<FileVersion> findByFileIdAndVersion(UUID fileId, Integer version);

        @Query(value = "SELECT * FROM file_versions WHERE file_id = :fileId " +
                        "ORDER BY version DESC LIMIT 1", nativeQuery = true)
        Optional<FileVersion> findLatestVersion(@Param("fileId") UUID fileId);

        long countByFileId(UUID fileId);

        List<FileVersion> findByFileIdAndCreatedByUserId(UUID fileId, UUID createdByUserId);

        @Query("SELECT COALESCE(SUM(fv.size), 0) FROM FileVersion fv WHERE fv.file.id = :fileId")
        Long calculateTotalVersionsSize(@Param("fileId") UUID fileId);

        List<FileVersion> findByHash(String hash);

        @Query("SELECT fv FROM FileVersion fv WHERE fv.file.id = :fileId " +
                        "ORDER BY fv.version DESC")
        List<FileVersion> findAllVersionsSorted(@Param("fileId") UUID fileId);
}
