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
import java.util.List;
import java.util.Map;
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
    private final com.fileservice.repository.FileShareRepository shareRepository;
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
        // Пропускаем проверку для папок (size=0), так как они не занимают место
        if (file.getSize() > 0) {
            try {
                boolean hasQuota = userServiceClient.checkQuota(file.getUserId(), file.getSize());
                if (!hasQuota) {
                    log.warn("Quota check failed for user {}: requested size={}", file.getUserId(), file.getSize());
                    throw new IllegalArgumentException(
                            "User storage quota exceeded. Please free up some space or upgrade your plan.");
                }
            } catch (RuntimeException e) {
                // Пробрасываем оригинальное сообщение об ошибке
                log.error("Error checking quota for user {}: {}", file.getUserId(), e.getMessage());
                throw new IllegalArgumentException("Failed to verify storage quota: " + e.getMessage(), e);
            }
        } else {
            log.debug("Skipping quota check for folder or zero-size file: userId={}, isFolder={}",
                    file.getUserId(), file.isFolder());
        }

        File savedFile = fileRepository.save(file);

        // Обновляем использованное место (только для файлов, не для папок)
        if (savedFile.getSize() > 0) {
            try {
                userServiceClient.updateStorageUsed(savedFile.getUserId(), savedFile.getSize());
            } catch (Exception e) {
                log.error("Failed to update storage used for user {}", savedFile.getUserId(), e);
            }
        }

        // Set storage path synchronously BEFORE upload URL generation
        // Format: files/{fileId}/v{version}/data (or just filename, but standardized)
        // We use a clean path structure that is deterministic
        String storagePath = String.format("files/%s/v%d/%s",
                savedFile.getId(), savedFile.getVersion(), "data"); // "data" is the standard blob name
        savedFile.setStoragePath(storagePath);
        savedFile = fileRepository.save(savedFile);

        // Получаем Upload URL и проставляем в transient поле
        if (!savedFile.isFolder()) {
            try {
                String uploadUrl = storageServiceClient.getUploadUrl(
                        savedFile.getId().toString(),
                        savedFile.getName(),
                        savedFile.getSize(),
                        savedFile.getMimeType(),
                        savedFile.getVersion());
                savedFile.setUploadUrl(uploadUrl);
            } catch (Exception e) {
                log.error("Failed to get upload url for file {}", savedFile.getId(), e);
            }
        }

        log.info("File created: id={}, name={}, userId={}",
                savedFile.getId(), savedFile.getName(), savedFile.getUserId());

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.uploaded")
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
        // Relaxing check to findById to allow shared access (permission checked by
        // controller/grpc)
        Optional<File> fileOpt = fileRepository.findById(fileId)
                .filter(file -> !file.isDeleted());

        fileOpt.ifPresent(file -> {
            if (!file.isFolder()) {
                try {
                    String downloadUrl = storageServiceClient.getDownloadUrl(file.getId().toString(),
                            file.getVersion());
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

        // Relaxing check to findById. Permission checked by GrpcService.
        File existingFile = fileRepository.findById(fileId)
                .filter(file -> !file.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found", fileId)));

        boolean contentChanged = false;

        // Check for name change BEFORE updating the entity
        String oldName = existingFile.getName();
        boolean nameChanged = updatedFile.getName() != null
                && !updatedFile.getName().equals(oldName);

        long oldSize = existingFile.getSize();

        // Обновляем поля
        if (updatedFile.getName() != null) {
            existingFile.setName(updatedFile.getName());
        }
        if (updatedFile.getMimeType() != null) {
            existingFile.setMimeType(updatedFile.getMimeType());
        }

        // Self-healing: Ensure storage_path is set before archiving if it's missing
        // (legacy/broken data)
        if (existingFile.getStoragePath() == null || existingFile.getStoragePath().isBlank()) {
            String healedPath = String.format("files/%s/v%d/data",
                    existingFile.getId(), existingFile.getVersion());
            existingFile.setStoragePath(healedPath);
            // Save immediately to ensure data consistency even if versioning fails later
            existingFile = fileRepository.save(existingFile);
            log.warn("Healed missing storage_path for file {}: {}", fileId, healedPath);
        }

        // --- QUOTA CHECK START ---
        // Если меняется размер, проверяем квоту ВЛАДЕЛЬЦА
        if (updatedFile.getSize() != null && updatedFile.getSize() > existingFile.getSize()) {
            long sizeDiff = updatedFile.getSize() - existingFile.getSize();
            try {
                // Check quota for OWNER (existingFile.getUserId()), not necessarily the actor
                // (userId)
                boolean hasQuota = userServiceClient.checkQuota(existingFile.getUserId(), sizeDiff);
                if (!hasQuota) {
                    log.warn("Quota check failed for owner {} during update by {}: sizeDiff={}",
                            existingFile.getUserId(), userId, sizeDiff);
                    throw new IllegalArgumentException(
                            "Owner's storage quota exceeded.");
                }
            } catch (RuntimeException e) {
                // Re-throw valid business exceptions
                if (e.getMessage().contains("quota"))
                    throw e;
                log.error("Error checking quota checks for owner {}: {}", existingFile.getUserId(), e.getMessage());
                // Optional: fail or proceed? Safety first -> fail?
                // Let's assume fail if service is reachable but returns false.
                // If service error, maybe block to prevent abuse.
                throw new IllegalArgumentException("Failed to verify storage quota during update.");
            }
        }
        // --- QUOTA CHECK END ---

        // Если меняется размер или хеш, считаем что меняется контент -> версионирование
        // ВАЖНО: Игнорируем size=0, так как это часто приходит при обновлении
        // метаданных (rename)
        // и не означает реальное зануление файла.
        boolean isSizeChanged = updatedFile.getSize() != null
                && updatedFile.getSize() > 0
                && !updatedFile.getSize().equals(existingFile.getSize());

        boolean isHashChanged = updatedFile.getHash() != null
                && !updatedFile.getHash().isEmpty()
                && !updatedFile.getHash().equals(existingFile.getHash());

        if (isSizeChanged || isHashChanged) {

            contentChanged = true;

            // Create new version (save OLD state)
            FileVersion version = FileVersion.builder()
                    .version(existingFile.getVersion())
                    .size(existingFile.getSize())
                    .hash(existingFile.getHash())
                    .storagePath(existingFile.getStoragePath())
                    .createdByUserId(userId)
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

            // Generate NEW storage path for the NEW version
            // Format: files/{fileId}/v{version}/data
            String newStoragePath = String.format("files/%s/v%d/%s",
                    existingFile.getId(), existingFile.getVersion(), "data");
            existingFile.setStoragePath(newStoragePath);
            log.debug("Rotated to new storage path: {}", newStoragePath);
        }

        if (updatedFile.getStoragePath() != null) {
            existingFile.setStoragePath(updatedFile.getStoragePath());
        }

        File savedFile = fileRepository.save(existingFile);

        // Update storage used if size changed
        if (updatedFile.getSize() != null && !updatedFile.getSize().equals(oldSize)) {
            long sizeDiff = updatedFile.getSize() - oldSize;
            try {
                userServiceClient.updateStorageUsed(savedFile.getUserId(), sizeDiff);
            } catch (Exception e) {
                log.error("Failed to update storage used for user {}", savedFile.getUserId(), e);
            }
        }

        log.info("File updated: id={}, userId={}", savedFile.getId(), userId);

        // Если контент меняется, или клиент явно просит - выдаем UploadUrl
        if (contentChanged) {
            try {
                String uploadUrl = storageServiceClient.getUploadUrl(
                        savedFile.getId().toString(),
                        savedFile.getName(),
                        savedFile.getSize(),
                        savedFile.getMimeType(),
                        savedFile.getVersion());
                savedFile.setUploadUrl(uploadUrl);
            } catch (Exception e) {
                log.error("Failed to get upload url for update file {}", savedFile.getId(), e);
            }
        }

        // Publish specific events based on what changed
        if (contentChanged) {
            // Version upload event
            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.version_uploaded")
                    .fileId(savedFile.getId())
                    .userId(savedFile.getUserId())
                    .timestamp(LocalDateTime.now())
                    .version(savedFile.getVersion())
                    .payload(savedFile)
                    .build());
        } else if (nameChanged) {
            // Rename event
            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.renamed")
                    .fileId(savedFile.getId())
                    .userId(savedFile.getUserId())
                    .timestamp(LocalDateTime.now())
                    .version(savedFile.getVersion())
                    .metadata(Map.of("oldName", oldName, "newName", savedFile.getName()))
                    .payload(savedFile)
                    .build());
        } else {
            // Generic update event
            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.updated")
                    .fileId(savedFile.getId())
                    .userId(savedFile.getUserId())
                    .timestamp(LocalDateTime.now())
                    .version(savedFile.getVersion())
                    .payload(savedFile)
                    .build());
        }

        // --- Notify shared users ---
        try {
            List<com.fileservice.model.FileShare> shares = shareRepository.findByFileId(savedFile.getId());
            for (com.fileservice.model.FileShare share : shares) {
                // Skip if the recipient is the one performing the action (actor)
                // Note: userId is the actor here.
                if (share.getSharedWithUserId().equals(userId))
                    continue;

                // Also skip if recipient is the owner (already notified above, if owner !=
                // actor)
                // But wait, above we notify savedFile.getUserId() (owner).
                // If actor (userId) != owner, owner gets notified.
                // If actor == owner, owner gets notified.
                // We just need to ensure we don't notify the same person twice via this loop.
                if (share.getSharedWithUserId().equals(savedFile.getUserId()))
                    continue;

                String eventType = contentChanged ? "file.version_uploaded"
                        : nameChanged ? "file.renamed" : "file.updated";

                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                if (nameChanged) {
                    metadata.put("oldName", oldName);
                    metadata.put("newName", savedFile.getName());
                }
                metadata.put("fileName", savedFile.getName());
                metadata.put("sharedBy", userId.toString()); // Actor who updated it

                eventPublisher.publish(FileEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .eventType(eventType)
                        .fileId(savedFile.getId())
                        .userId(share.getSharedWithUserId()) // Target recipient
                        .timestamp(LocalDateTime.now())
                        .version(savedFile.getVersion())
                        .metadata(metadata)
                        .payload(savedFile)
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to notify shared users for file update: {}", savedFile.getId(), e);
        }
        // ---------------------------

        return savedFile;
    }

    /**
     * Мягкое удаление файла (is_deleted = true)
     */
    /**
     * Удаление файла.
     * Если файл активен -> Мягкое удаление (is_deleted = true).
     * Если файл уже в корзине -> Окончательное удаление.
     */
    public void deleteFile(UUID fileId, UUID userId) {
        log.debug("Deleting file: id={}, userId={}", fileId, userId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("File with id %s not found", fileId)));

        // Получаем всех пользователей, с которыми расшарен файл, чтобы уведомить их
        List<com.fileservice.model.FileShare> shares = shareRepository.findByFileId(fileId);

        if (file.isDeleted()) {
            // HARD DELETE
            log.info("Permanently deleting file: id={}, userId={}", fileId, userId);

            // 1. Физическое удаление из хранилища (если это файл)
            if (!file.isFolder()) {
                try {
                    // Удаляем текущий файл (все версии удаляются в storage-service если не указана
                    // версия,
                    // или storage-service просто чистит по префиксу.
                    // В данном случае deleteFile(fileId, null) удаляет все versions в
                    // StorageService)
                    storageServiceClient.deleteFile(file.getId().toString(), null);
                } catch (Exception e) {
                    log.error("Failed to delete file {} from storage during hard delete", file.getId(), e);
                    // Продолжаем удаление из БД, чтобы не оставлять мусор, даже если в S3 останется
                }
            }

            // 2. Освобождаем квоту
            if (file.getSize() > 0) {
                try {
                    // Use file.getUserId() (owner) for quota update
                    userServiceClient.updateStorageUsed(file.getUserId(), -file.getSize());
                } catch (Exception e) {
                    log.error("Failed to update storage used (decrement) for user {}", file.getUserId(), e);
                }
            }

            // 3. Удаляем из БД
            fileRepository.delete(file);

            // Уведомляем владельца
            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.hard_deleted")
                    .fileId(fileId)
                    .userId(userId) // Triggered by actor
                    .timestamp(LocalDateTime.now())
                    .version(file.getVersion())
                    .payload(file)
                    .build());

        } else {
            // SOFT DELETE
            file.softDelete();
            fileRepository.save(file);

            log.info("File soft deleted: id={}, userId={}", fileId, userId);

            // Уведомляем владельца
            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.deleted")
                    .fileId(fileId)
                    .userId(userId)
                    .timestamp(LocalDateTime.now())
                    .version(file.getVersion())
                    .payload(file)
                    .build());
        }

        // Уведомляем всех пользователей, с которыми был расшарен файл
        for (com.fileservice.model.FileShare share : shares) {
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("fileName", file.getName());
            metadata.put("ownerId", file.getUserId().toString());

            eventPublisher.publish(FileEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("file.unshared")
                    .fileId(fileId)
                    .userId(share.getSharedWithUserId()) // Notify the user who lost access
                    .timestamp(LocalDateTime.now())
                    .version(file.getVersion())
                    .metadata(metadata)
                    .build());
        }
    }

    /**
     * Перемещение файла или папки
     */
    public File moveFile(UUID fileId, UUID newParentId, UUID userId) {
        log.debug("Moving file: fileId={}, newParentId={}, userId={}", fileId, newParentId, userId);

        File file = fileRepository.findById(fileId)
                .filter(f -> !f.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        File newParent = null;
        if (newParentId != null) {
            newParent = fileRepository.findById(newParentId)
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
            // Check if parent itself is deleted
            Optional<File> parent = fileRepository.findById(parentFolderId);
            if (parent.isPresent() && parent.get().isDeleted()) {
                return Page.empty();
            }
            return fileRepository.findByUserIdAndParentFolderIdAndIsDeletedFalse(
                    userId, parentFolderId, pageable);
        }
    }

    /**
     * Получение файлов из корзины
     */
    @Transactional(readOnly = true)
    public Page<File> listTrash(UUID userId, Pageable pageable) {
        log.debug("Listing trash for user: {}", userId);
        return fileRepository.findByUserIdAndIsDeletedTrue(userId, pageable);
    }

    /**
     * Восстановление файла из корзины
     */
    public void restoreFile(UUID fileId, UUID userId) {
        log.debug("Restoring file: id={}, userId={}", fileId, userId);

        File file = fileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (!file.isDeleted()) {
            return; // Idempotent
        }

        // Рекурсивно восстанавливаем родителей, если они были удалены (Google Drive
        // style)
        restoreParentChain(file);

        file.restore();
        fileRepository.save(file);

        log.info("File restored: id={}, userId={}", fileId, userId);

        eventPublisher.publish(FileEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("file.restored")
                .fileId(fileId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .version(file.getVersion())
                .payload(file)
                .build());
    }

    private void restoreParentChain(File file) {
        File parent = file.getParentFolder();
        if (parent != null && parent.isDeleted()) {
            log.info("Automatically restoring parent folder {} for file {}", parent.getId(), file.getId());
            restoreParentChain(parent);
            parent.restore();
            fileRepository.save(parent);
        }
    }

    /**
     * Окончательное удаление всех файлов из корзины
     */
    public void emptyTrash(UUID userId) {
        log.info("Emptying trash for user: {}", userId);

        // 1. Получаем список всех удаленных файлов
        List<File> trashFiles = fileRepository.findAllByUserIdAndIsDeletedTrue(userId);

        long totalReleasedSpace = 0;

        // 2. Для каждого файла вызываем физическое удаление
        for (File file : trashFiles) {
            if (!file.isFolder()) {
                try {
                    // Пытаемся удалить из хранилища
                    storageServiceClient.deleteFile(file.getId().toString(), null);
                    totalReleasedSpace += file.getSize();
                } catch (Exception e) {
                    log.error("Failed to delete file {} from storage during empty trash", file.getId(), e);
                    // Продолжаем, но не удаляем из БД этот файл, чтобы можно было попробовать
                    // позже?
                    // Или удаляем все равно? Пользователь ожидает очистки.
                    // Обычно лучше не удалять из БД если физически файл остался,
                    // чтобы не "забыть" про него в хранилище.
                    continue;
                }
            }

            // 3. Только если успешно (или если папка) -> удаляем из БД
            // Для папок физического удаления в StorageService нет (они обычно transient в
            // S3/MinIO)
            fileRepository.delete(file);
        }

        // 4. Обновляем квоту пользователя (освобождаем место)
        if (totalReleasedSpace > 0) {
            try {
                userServiceClient.updateStorageUsed(userId, -totalReleasedSpace);
                log.info("User quota updated: released {} bytes for user {}", totalReleasedSpace, userId);
            } catch (Exception e) {
                log.error("Failed to update storage used (decrement) after emptying trash for user {}", userId, e);
            }
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
