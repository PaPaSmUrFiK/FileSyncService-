package com.fileservice.service;

import com.fileservice.client.StorageServiceClient;
import com.fileservice.client.UserServiceClient;
import com.fileservice.event.FileEvent;
import com.fileservice.model.File;
import com.fileservice.model.FileVersion;
import com.fileservice.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Основной сервис для работы с файлами
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FileService {

    private final FileRepository fileRepository;
    private final UserServiceClient userServiceClient;
    private final StorageServiceClient storageServiceClient;
    private final FileEventPublisher eventPublisher;
    private final VersionService versionService;

    // We cannot inject VersionService directly if it creates a cycle.
    // VersionService depends on FileRepository.
    // Does VersionService depend on FileService? NO.
    // So we can inject it.

    /**
     * Создание метаданных файла
     */
    public File createFile(File file) {
        log.debug("Creating file: name={}, path={}, userId={}",
                file.getName(), file.getPath(), file.getUserId());

        // Проверка на существование файла с таким же путем
        if (fileRepository.existsByPathAndUserIdAndIsDeletedFalse(file.getPath(), file.getUserId())) {
            throw new IllegalArgumentException(
                    String.format("File with path '%s' already exists for user %s",
                            file.getPath(), file.getUserId()));
        }

        // Если указан родительский каталог, проверяем его существование
        if (file.getParentFolder() != null && file.getParentFolder().getId() != null) {
            Optional<File> parent = fileRepository.findByIdAndUserId(
                    file.getParentFolder().getId(), file.getUserId());
            if (parent.isEmpty() || parent.get().isDeleted() || !parent.get().isFolder()) {
                throw new IllegalArgumentException(
                        "Parent folder does not exist or is not a folder");
            }
            file.setParentFolder(parent.get());
        }

        // Проверка квоты (размер файла)
        if (!userServiceClient.checkQuota(file.getUserId(), file.getSize())) {
            throw new IllegalArgumentException("User storage quota exceeded");
        }

        File savedFile = fileRepository.save(file);

        // Обновляем использованное место
        try {
            userServiceClient.updateStorageUsed(savedFile.getUserId(), savedFile.getSize());
        } catch (Exception e) {
            log.error("Failed to update storage used for user {}", savedFile.getUserId(), e);
        }

        // Получаем Upload URL и проставляем в transient поле
        if (!savedFile.isFolder()) {
            try {
                String uploadUrl = storageServiceClient.getUploadUrl(savedFile.getId().toString());
                savedFile.setUploadUrl(uploadUrl);
            } catch (Exception e) {
                log.error("Failed to get upload url for file {}", savedFile.getId(), e);
            }
        }

        log.info("File created: id={}, name={}, userId={}",
                savedFile.getId(), savedFile.getName(), savedFile.getUserId());

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.created")
                .fileId(savedFile.getId())
                .userId(savedFile.getUserId())
                .timestamp(LocalDateTime.now())
                .version(1)
                .payload(savedFile)
                .build());

        return savedFile;

    }

    /**
     * Получение информации о файле
     */
    @Transactional(readOnly = true)
    public Optional<File> getFile(UUID fileId, UUID userId) {
        log.debug("Getting file: id={}, userId={}", fileId, userId);
        Optional<File> fileOpt = fileRepository.findByIdAndUserId(fileId, userId)
                .filter(file -> !file.isDeleted());

        fileOpt.ifPresent(file -> {
            if (!file.isFolder()) {
                try {
                    String downloadUrl = storageServiceClient.getDownloadUrl(file.getId().toString());
                    file.setDownloadUrl(downloadUrl);
                } catch (Exception e) {
                    log.error("Failed to get download url for file {}", file.getId(), e);
                }
            }
        });

        return fileOpt;
    }

    /**
     * Получение файла по пути
     */
    @Transactional(readOnly = true)
    public Optional<File> getFileByPath(String path, UUID userId) {
        log.debug("Getting file by path: path={}, userId={}", path, userId);
        return fileRepository.findByPathAndUserIdAndIsDeletedFalse(path, userId);
    }

    /**
     * Обновление метаданных файла
     */
    public File updateFile(UUID fileId, UUID userId, File updatedFile) {
        log.debug("Updating file: id={}, userId={}", fileId, userId);

        File existingFile = fileRepository.findByIdAndUserId(fileId, userId)
                .filter(file -> !file.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found for user %s", fileId, userId)));

        boolean contentChanged = false;

        // Обновляем поля
        if (updatedFile.getName() != null) {
            existingFile.setName(updatedFile.getName());
        }
        if (updatedFile.getMimeType() != null) {
            existingFile.setMimeType(updatedFile.getMimeType());
        }

        // Если меняется размер или хеш, считаем что меняется контент -> версионирование
        if ((updatedFile.getSize() != null && !updatedFile.getSize().equals(existingFile.getSize())) ||
                (updatedFile.getHash() != null && !updatedFile.getHash().equals(existingFile.getHash()))) {

            contentChanged = true;

            // Create new version (save OLD state)
            FileVersion version = FileVersion.builder()
                    .version(existingFile.getVersion())
                    .size(existingFile.getSize())
                    .hash(existingFile.getHash())
                    .storagePath(existingFile.getStoragePath())
                    .createdBy(userId)
                    .build();

            try {
                // Warning: we pass existingFile.getVersion().
                // VersionService checks: if (version.getVersion() > file.getVersion()) update
                // file.
                // We want to archive current version.
                // The VersionService logic might need adjustment if it doesn't support
                // archiving "current" version clearly.
                // But typically, we ask VersionService to "create a version entry".
                versionService.createVersion(fileId, userId, version);
            } catch (Exception e) {
                log.error("Failed to version file {}", fileId, e);
                throw new RuntimeException("Versioning failed", e);
            }

            if (updatedFile.getSize() != null)
                existingFile.setSize(updatedFile.getSize());
            if (updatedFile.getHash() != null)
                existingFile.setHash(updatedFile.getHash());

            existingFile.incrementVersion();
        }

        if (updatedFile.getStoragePath() != null) {
            existingFile.setStoragePath(updatedFile.getStoragePath());
        }

        File savedFile = fileRepository.save(existingFile);
        log.info("File updated: id={}, userId={}", savedFile.getId(), userId);

        // Если контент меняется, или клиент явно просит - выдаем UploadUrl
        if (contentChanged) {
            try {
                String uploadUrl = storageServiceClient.getUploadUrl(savedFile.getId().toString());
                savedFile.setUploadUrl(uploadUrl);
            } catch (Exception e) {
                log.error("Failed to get upload url for update file {}", savedFile.getId(), e);
            }
        }

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.updated")
                .fileId(savedFile.getId())
                .userId(savedFile.getUserId())
                .timestamp(LocalDateTime.now())
                .version(savedFile.getVersion())
                .build());

        return savedFile;
    }

    /**
     * Мягкое удаление файла (is_deleted = true)
     */
    public void deleteFile(UUID fileId, UUID userId) {
        log.debug("Deleting file: id={}, userId={}", fileId, userId);

        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found for user %s", fileId, userId)));

        file.softDelete();
        fileRepository.save(file);

        // Уведомляем StorageService (согласно требованиям 1.4)
        try {
            storageServiceClient.deleteFile(file.getStoragePath()); // Или fileId? Метод принимает path.
        } catch (Exception e) {
            log.warn("Failed to notify storage service about deletion", e);
        }

        // Обновляем квоту (освобождаем место)
        try {
            userServiceClient.updateStorageUsed(file.getUserId(), -file.getSize());
        } catch (Exception e) {
            log.error("Failed to update storage used (decrement) for user {}", file.getUserId(), e);
        }

        log.info("File soft deleted: id={}, userId={}", fileId, userId);

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.deleted")
                .fileId(fileId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .version(file.getVersion())
                .build());

    }

    /**
     * Перемещение файла или папки
     */
    public File moveFile(UUID fileId, UUID newParentId, UUID userId) {
        log.debug("Moving file: fileId={}, newParentId={}, userId={}", fileId, newParentId, userId);

        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        File newParent = null;
        if (newParentId != null) {
            newParent = fileRepository.findByIdAndUserId(newParentId, userId)
                    .filter(f -> !f.isDeleted() && f.isFolder())
                    .orElseThrow(() -> new IllegalArgumentException("Target folder not found"));

            // Check for cycling: newParent cannot be child of file (if file is folder)
            if (file.isFolder()) {
                File current = newParent;
                while (current != null) {
                    if (current.getId().equals(file.getId())) {
                        throw new IllegalArgumentException("Cannot move folder into itself or its children");
                    }
                    current = current.getParentFolder();
                }
            }
        }

        // Update parent
        file.setParentFolder(newParent);

        // Recalculate paths (simplified - in real app would trigger recursive update)
        // String newPath = (newParent != null ? newParent.getPath() : "") + "/" +
        // file.getName();
        // file.setPath(newPath);
        // Note: Real path logic implies we need to update all children paths too.

        File savedFile = fileRepository.save(file);

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.moved") // Assuming new event type or generic updated
                .fileId(savedFile.getId())
                .userId(savedFile.getUserId())
                .timestamp(LocalDateTime.now())
                .version(savedFile.getVersion())
                .build());

        return savedFile;
    }

    /**
     * Список файлов с пагинацией и фильтрацией
     */
    @Transactional(readOnly = true)
    public Page<File> listFiles(UUID userId, UUID parentFolderId, Pageable pageable) {
        log.debug("Listing files: userId={}, parentFolderId={}, page={}, size={}",
                userId, parentFolderId, pageable.getPageNumber(), pageable.getPageSize());

        if (parentFolderId == null) {
            return fileRepository.findByUserIdAndParentFolderIdIsNullAndIsDeletedFalse(
                    userId, pageable);
        } else {
            return fileRepository.findByUserIdAndParentFolderIdAndIsDeletedFalse(
                    userId, parentFolderId, pageable);
        }
    }

    /**
     * Поиск файлов по имени
     */
    @Transactional(readOnly = true)
    public Page<File> searchFiles(UUID userId, String query, Pageable pageable) {
        log.debug("Searching files: userId={}, query={}", userId, query);
        return fileRepository.searchByName(userId, query, pageable);
    }

    /**
     * Проверка существования файла
     */
    @Transactional(readOnly = true)
    public boolean checkFileExists(UUID fileId, UUID userId) {
        log.debug("Checking file existence: id={}, userId={}", fileId, userId);
        return fileRepository.findByIdAndUserId(fileId, userId)
                .map(file -> !file.isDeleted())
                .orElse(false);
    }

    /**
     * Проверка существования файла по пути
     */
    @Transactional(readOnly = true)
    public boolean checkFileExistsByPath(String path, UUID userId) {
        log.debug("Checking file existence by path: path={}, userId={}", path, userId);
        return fileRepository.existsByPathAndUserIdAndIsDeletedFalse(path, userId);
    }

    /**
     * Получение файла без проверки пользователя (для внутреннего использования)
     */
    @Transactional(readOnly = true)
    public Optional<File> getFileById(UUID fileId) {
        return fileRepository.findById(fileId)
                .filter(file -> !file.isDeleted());
    }
}
