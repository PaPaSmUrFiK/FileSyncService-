package com.fileservice.service;

import com.fileservice.client.StorageServiceClient;
import com.fileservice.event.FileEvent;
import com.fileservice.model.File;
import com.fileservice.model.FileVersion;
import com.fileservice.repository.FileRepository;
import com.fileservice.repository.FileVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для управления версиями файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VersionService {

    private final FileVersionRepository versionRepository;
    private final FileRepository fileRepository;
    private final StorageServiceClient storageServiceClient;
    private final FileEventPublisher eventPublisher;

    @Value("${file-service.versioning.max-versions-per-file:10}")
    private int maxVersionsPerFile;

    @Value("${file-service.versioning.retention-days:30}")
    private int retentionDays;

    /**
     * Создание новой версии файла
     */
    public FileVersion createVersion(UUID fileId, UUID userId, FileVersion version) {
        log.debug("Creating version for file: fileId={}, userId={}, version={}",
                fileId, userId, version.getVersion());

        File file = fileRepository.findById(fileId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found", fileId)));

        // Проверка, что версия не существует
        if (versionRepository.findByFileIdAndVersion(fileId, version.getVersion()).isPresent()) {
            throw new IllegalArgumentException(
                    String.format("Version %d already exists for file %s",
                            version.getVersion(), fileId));
        }

        version.setFile(file);
        version.setCreatedByUserId(userId);

        FileVersion savedVersion = versionRepository.save(version);

        // Обновляем версию файла
        if (version.getVersion() > file.getVersion()) {
            file.setVersion(version.getVersion());
            fileRepository.save(file);
        }

        // Проверяем лимит версий и удаляем старые при необходимости
        deleteOldVersionsIfNeeded(fileId);

        log.info("Version created: id={}, fileId={}, version={}",
                savedVersion.getId(), fileId, savedVersion.getVersion());
        return savedVersion;
    }

    /**
     * Получение списка версий файла
     */
    @Transactional(readOnly = true)
    public Page<FileVersion> getVersions(UUID fileId, Pageable pageable) {
        log.debug("Getting versions for file: fileId={}, page={}, size={}",
                fileId, pageable.getPageNumber(), pageable.getPageSize());
        return versionRepository.findByFileIdOrderByVersionDesc(fileId, pageable);
    }

    /**
     * Получение всех версий файла (без пагинации)
     */
    @Transactional(readOnly = true)
    public List<FileVersion> getAllVersions(UUID fileId) {
        log.debug("Getting all versions for file: fileId={}", fileId);
        return versionRepository.findByFileIdOrderByVersionDesc(fileId);
    }

    /**
     * Получение конкретной версии
     */
    @Transactional(readOnly = true)
    public Optional<FileVersion> getVersion(UUID fileId, Integer versionNumber) {
        log.debug("Getting version: fileId={}, version={}", fileId, versionNumber);
        return versionRepository.findByFileIdAndVersion(fileId, versionNumber);
    }

    /**
     * Получение последней версии
     */
    @Transactional(readOnly = true)
    public Optional<FileVersion> getLatestVersion(UUID fileId) {
        log.debug("Getting latest version for file: fileId={}", fileId);
        return versionRepository.findLatestVersion(fileId);
    }

    /**
     * Восстановление версии (создание новой версии на основе старой)
     */
    public FileVersion restoreVersion(UUID fileId, Integer versionNumber, UUID userId) {
        log.debug("Restoring version: fileId={}, version={}, userId={}",
                fileId, versionNumber, userId);

        FileVersion oldVersion = versionRepository.findByFileIdAndVersion(fileId, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Version %d not found for file %s", versionNumber, fileId)));

        File file = oldVersion.getFile();
        if (file == null || file.isDeleted()) {
            throw new IllegalArgumentException("File not found or deleted");
        }

        // 1. Archive CURRENT version (e.g. v2) before overwriting it
        // We need to save the state that is about to be lost (the current state of the
        // 'file' entity)
        FileVersion currentVersionArchive = FileVersion.builder()
                .file(file)
                .version(file.getVersion())
                .size(file.getSize())
                .hash(file.getHash())
                .storagePath(file.getStoragePath())
                // Attribute the archiving to the user performing the restore,
                // or ideally the original creator if we knew.
                // Using file owner or current user is acceptable.
                .createdByUserId(userId)
                .build();

        versionRepository.save(currentVersionArchive);

        // 2. Prepare new version number
        // The file is moving forward to a new version (v3), which happens to look like
        // v1.
        int newVersionNumber = file.getVersion() + 1;

        // 3. Update File to match the OLD version (content-wise) but with NEW version
        // number
        file.setVersion(newVersionNumber);
        file.setSize(oldVersion.getSize());
        file.setHash(oldVersion.getHash());
        file.setStoragePath(oldVersion.getStoragePath());
        fileRepository.save(file);

        // Synchronously register the new version metadata in StorageService to prevent
        // race conditions during download
        storageServiceClient.saveVersionMetadata(
                fileId.toString(),
                newVersionNumber,
                oldVersion.getStoragePath(),
                oldVersion.getSize());

        log.info("Version restored: oldVersion={}, newVersion={}, fileId={}",
                versionNumber, newVersionNumber, fileId);

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.restored")
                .fileId(fileId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .version(newVersionNumber)
                .payload(file) // Payload is the new file state
                .build());

        // Return a representation of the new version (which is the file itself now)
        return FileVersion.builder()
                .file(file)
                .version(newVersionNumber)
                .size(file.getSize())
                .hash(file.getHash())
                .storagePath(file.getStoragePath())
                .createdByUserId(userId)
                .createdAt(LocalDateTime.now())
                .build();

    }

    /**
     * Удаление старых версий (по лимиту и по времени)
     */
    public void deleteOldVersions(UUID fileId) {
        log.debug("Deleting old versions for file: fileId={}", fileId);

        List<FileVersion> versions = versionRepository.findByFileIdOrderByVersionDesc(fileId);

        if (versions.size() <= maxVersionsPerFile) {
            log.debug("No versions to delete: current count={}, max={}",
                    versions.size(), maxVersionsPerFile);
            return;
        }

        // Удаляем версии сверх лимита
        List<FileVersion> versionsToDelete = versions.subList(maxVersionsPerFile, versions.size());
        versionRepository.deleteAll(versionsToDelete);

        log.info("Deleted {} old versions for file: fileId={}",
                versionsToDelete.size(), fileId);

        // Clean up storage for deleted versions
        for (FileVersion v : versionsToDelete) {
            try {
                // Warning: deduplication check is needed in real word
                storageServiceClient.deleteFile(v.getFile().getId().toString(), v.getVersion());
            } catch (Exception e) {
                log.warn("Failed to delete version file from storage: fileId={}, version={}",
                        v.getFile().getId(), v.getVersion());
            }
        }

    }

    /**
     * Удаление версий старше retentionDays дней
     */
    public void deleteVersionsOlderThan(UUID fileId, int days) {
        log.debug("Deleting versions older than {} days for file: fileId={}", days, fileId);

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        List<FileVersion> allVersions = versionRepository.findByFileIdOrderByVersionDesc(fileId);

        List<FileVersion> versionsToDelete = allVersions.stream()
                .filter(v -> v.getCreatedAt().isBefore(cutoffDate))
                .filter(v -> !v.isLatestVersion()) // Не удаляем последнюю версию
                .toList();

        if (!versionsToDelete.isEmpty()) {
            versionRepository.deleteAll(versionsToDelete);
            log.info("Deleted {} versions older than {} days for file: fileId={}",
                    versionsToDelete.size(), days, fileId);

            // Clean up storage
            for (FileVersion v : versionsToDelete) {
                try {
                    storageServiceClient.deleteFile(v.getFile().getId().toString(), v.getVersion());
                } catch (Exception e) {
                    log.warn("Failed to delete version file from storage: fileId={}, version={}",
                            v.getFile().getId(), v.getVersion());
                }
            }

        }
    }

    /**
     * Удаление старых версий по умолчанию (retentionDays)
     */
    public void deleteOldVersionsByRetention(UUID fileId) {
        deleteVersionsOlderThan(fileId, retentionDays);
    }

    /**
     * Проверка и удаление старых версий при необходимости
     */
    private void deleteOldVersionsIfNeeded(UUID fileId) {
        List<FileVersion> versions = versionRepository.findByFileIdOrderByVersionDesc(fileId);
        if (versions.size() > maxVersionsPerFile) {
            deleteOldVersions(fileId);
        }
    }

    /**
     * Подсчет общего размера всех версий файла
     */
    @Transactional(readOnly = true)
    public Long calculateTotalVersionsSize(UUID fileId) {
        return versionRepository.calculateTotalVersionsSize(fileId);
    }

    /**
     * Подсчет количества версий
     */
    @Transactional(readOnly = true)
    public long countVersions(UUID fileId) {
        return versionRepository.countByFileId(fileId);
    }
}
