package com.fileservice.service;

import com.fileservice.model.File;
import com.fileservice.model.FilePermission;
import com.fileservice.model.FileShare;
import com.fileservice.model.SharePermission;
import com.fileservice.repository.FilePermissionRepository;
import com.fileservice.repository.FileRepository;
import com.fileservice.repository.FileShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для проверки прав доступа к файлам
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PermissionService {

    private final FilePermissionRepository permissionRepository;
    private final FileShareRepository shareRepository;
    private final FileRepository fileRepository;

    /**
     * Проверка прав доступа
     */
    public boolean checkPermission(UUID fileId, UUID userId,
            FilePermission.PermissionType requiredPermission) {
        log.debug("Checking permission: fileId={}, userId={}, requiredPermission={}",
                fileId, userId, requiredPermission);

        // Проверка существования файла
        // Проверка существования файла
        Optional<File> fileOpt = fileRepository.findById(fileId);

        if (fileOpt.isEmpty()) {
            return false;
        }

        File file = fileOpt.get();

        // Владелец файла имеет все права
        if (file.getUserId().equals(userId)) {
            return true;
        }

        // Проверка прямых разрешений
        Optional<FilePermission> permission = permissionRepository
                .findHighestPermission(fileId, userId);

        if (permission.isPresent()) {
            FilePermission.PermissionType userPermission = permission.get().getPermission();
            if (userPermission.includes(requiredPermission)) {
                return true;
            }
        }

        // Проверка расшаривания
        Optional<FileShare> share = shareRepository
                .findByFileIdAndSharedWithUserId(fileId, userId);

        if (share.isPresent() && share.get().isActive()) {
            SharePermission sharePermission = share.get().getPermission();

            // Маппинг SharePermission в PermissionType
            return switch (requiredPermission) {
                case READ -> sharePermission.canRead();
                case WRITE -> sharePermission.canWrite();
                case DELETE, SHARE, ADMIN -> sharePermission.canAdmin();
            };
        }

        return false;
    }

    /**
     * Проверка права на чтение
     */
    public boolean hasReadAccess(UUID fileId, UUID userId) {
        return checkPermission(fileId, userId, FilePermission.PermissionType.READ);
    }

    /**
     * Проверка права на запись
     */
    public boolean hasWriteAccess(UUID fileId, UUID userId) {
        return checkPermission(fileId, userId, FilePermission.PermissionType.WRITE);
    }

    /**
     * Проверка права на удаление
     */
    public boolean hasDeleteAccess(UUID fileId, UUID userId) {
        return checkPermission(fileId, userId, FilePermission.PermissionType.DELETE);
    }

    /**
     * Проверка права на администрирование
     */
    public boolean hasAdminAccess(UUID fileId, UUID userId) {
        return checkPermission(fileId, userId, FilePermission.PermissionType.ADMIN);
    }

    /**
     * Получение уровня доступа пользователя к файлу
     */
    public Optional<FilePermission.PermissionType> getUserPermission(UUID fileId, UUID userId) {
        log.debug("Getting user permission: fileId={}, userId={}", fileId, userId);

        // Проверка существования файла
        // Проверка существования файла
        Optional<File> fileOpt = fileRepository.findById(fileId);

        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }

        File file = fileOpt.get();

        // Владелец файла имеет ADMIN права
        if (file.getUserId().equals(userId)) {
            return Optional.of(FilePermission.PermissionType.ADMIN);
        }

        // Проверка прямых разрешений
        Optional<FilePermission> permission = permissionRepository
                .findHighestPermission(fileId, userId);

        if (permission.isPresent()) {
            return Optional.of(permission.get().getPermission());
        }

        // Проверка расшаривания
        Optional<FileShare> share = shareRepository
                .findByFileIdAndSharedWithUserId(fileId, userId);

        if (share.isPresent() && share.get().isActive()) {
            SharePermission sharePermission = share.get().getPermission();

            // Конвертация SharePermission в PermissionType
            FilePermission.PermissionType permissionType = switch (sharePermission) {
                case READ -> FilePermission.PermissionType.READ;
                case WRITE -> FilePermission.PermissionType.WRITE;
                case ADMIN -> FilePermission.PermissionType.ADMIN;
            };

            return Optional.of(permissionType);
        }

        return Optional.empty();
    }

    /**
     * Проверка, является ли пользователь владельцем файла
     */
    public boolean isOwner(UUID fileId, UUID userId) {
        return fileRepository.findByIdAndUserId(fileId, userId)
                .isPresent();
    }

    /**
     * Проверка наличия любого доступа (чтение, запись, администрирование)
     */
    public boolean hasAnyAccess(UUID fileId, UUID userId) {
        return hasReadAccess(fileId, userId) ||
                hasWriteAccess(fileId, userId) ||
                hasAdminAccess(fileId, userId);
    }

    /**
     * Проверка права на расшаривание
     */
    public boolean hasShareAccess(UUID fileId, UUID userId) {
        return checkPermission(fileId, userId, FilePermission.PermissionType.SHARE);
    }

    /**
     * Получение всех разрешений пользователя для файла
     */
    public java.util.List<FilePermission> getUserPermissions(UUID fileId, UUID userId) {
        return permissionRepository.findByFileIdAndUserId(fileId, userId);
    }

    /**
     * Проверка доступа с учетом расшаривания (для внутреннего использования)
     */
    public boolean checkAccessViaShare(UUID fileId, UUID userId) {
        Optional<FileShare> share = shareRepository
                .findByFileIdAndSharedWithUserId(fileId, userId);
        return share.isPresent() && share.get().isActive();
    }
}
