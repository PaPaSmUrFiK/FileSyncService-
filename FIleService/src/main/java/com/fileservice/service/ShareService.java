package com.fileservice.service;

import com.fileservice.event.FileEvent;
import com.fileservice.model.File;
import com.fileservice.model.FileShare;
import com.fileservice.model.SharePermission;
import com.fileservice.repository.FileRepository;
import com.fileservice.repository.FileShareRepository;
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
 * Сервис для управления расшариванием файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShareService {

        private final FileShareRepository shareRepository;
        private final FileRepository fileRepository;
        private final FileEventPublisher eventPublisher;

        @Value("${file-service.share.max-shares-per-file:50}")
        private int maxSharesPerFile;

        @Value("${file-service.share.default-expiry-days:30}")
        private int defaultExpiryDays;

        /**
         * Создание доступа к файлу (расшаривание)
         */
        public FileShare shareFile(UUID fileId, UUID ownerId, UUID sharedWithUserId,
                        SharePermission permission, LocalDateTime expiresAt) {
                log.debug("Sharing file: fileId={}, ownerId={}, sharedWithUserId={}, permission={}",
                                fileId, ownerId, sharedWithUserId, permission);

                // Проверка существования файла
                File file = fileRepository.findByIdAndUserId(fileId, ownerId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found for owner %s", fileId,
                                                                ownerId)));

                // Нельзя расшарить файл самому себе
                if (ownerId.equals(sharedWithUserId)) {
                        throw new IllegalArgumentException("Cannot share file with yourself");
                }

                // Проверка лимита расшариваний
                long activeSharesCount = shareRepository.countActiveShares(fileId, LocalDateTime.now());
                if (activeSharesCount >= maxSharesPerFile) {
                        throw new IllegalStateException(
                                        String.format("Maximum number of shares (%d) reached for file %s",
                                                        maxSharesPerFile, fileId));
                }

                // Проверка, не расшарен ли уже файл этому пользователю
                Optional<FileShare> existingShare = shareRepository
                                .findByFileIdAndSharedWithUserId(fileId, sharedWithUserId);

                if (existingShare.isPresent()) {
                        FileShare share = existingShare.get();
                        // Обновляем существующее расшаривание
                        share.setPermission(permission);
                        share.setExpiresAt(expiresAt);
                        FileShare updatedShare = shareRepository.save(share);
                        log.info("Share updated: id={}, fileId={}, sharedWithUserId={}",
                                        updatedShare.getId(), fileId, sharedWithUserId);
                        return updatedShare;
                }

                // Устанавливаем дату истечения по умолчанию, если не указана
                if (expiresAt == null) {
                        expiresAt = LocalDateTime.now().plusDays(defaultExpiryDays);
                }

                // Создаем новое расшаривание
                FileShare share = FileShare.builder()
                                .file(file)
                                .createdBy(ownerId)
                                .sharedWithUserId(sharedWithUserId)
                                .permission(permission)
                                .expiresAt(expiresAt)
                                .isActive(true)
                                .build();

                FileShare savedShare = shareRepository.save(share);
                log.info("File shared: id={}, fileId={}, ownerId={}, sharedWithUserId={}, permission={}",
                                savedShare.getId(), fileId, ownerId, sharedWithUserId, permission);

                // Создаем metadata для события
                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                metadata.put("fileName", file.getName());
                metadata.put("sharedWithUserId", sharedWithUserId.toString());
                metadata.put("permission", permission.name());

                eventPublisher.publish(FileEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("file.shared")
                                .fileId(fileId)
                                .userId(ownerId)
                                .timestamp(LocalDateTime.now())
                                .version(1)
                                .payload(savedShare)
                                .metadata(metadata)
                                .build());

                return savedShare;

        }

        /**
         * Отзыв доступа (удаление расшаривания)
         */
        public void revokeShare(UUID fileId, UUID ownerId, UUID sharedWithUserId) {
                log.debug("Revoking share: fileId={}, ownerId={}, sharedWithUserId={}",
                                fileId, ownerId, sharedWithUserId);

                FileShare share = shareRepository.findByFileIdAndSharedWithUserId(fileId, sharedWithUserId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("Share not found for file %s and user %s",
                                                                fileId, sharedWithUserId)));

                // Проверка, что отзыв делает владелец
                if (!share.getOwnerId().equals(ownerId)) {
                        throw new IllegalArgumentException(
                                        "Only file owner can revoke shares");
                }

                // Получаем информацию о файле для уведомления
                File file = fileRepository.findById(fileId)
                                .orElseThrow(() -> new IllegalArgumentException("File not found"));

                shareRepository.delete(share);
                log.info("Share revoked: fileId={}, sharedWithUserId={}", fileId, sharedWithUserId);

                // Уведомляем пользователя об отзыве доступа
                java.util.Map<String, String> metadata = new java.util.HashMap<>();
                metadata.put("fileName", file.getName());
                metadata.put("ownerId", ownerId.toString());

                eventPublisher.publish(FileEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("file.unshared")
                                .fileId(fileId)
                                .userId(sharedWithUserId) // Notify the user who lost access
                                .timestamp(LocalDateTime.now())
                                .version(1)
                                .metadata(metadata)
                                .build());
        }

        /**
         * Отзыв всех расшариваний для файла
         */
        public void revokeAllShares(UUID fileId, UUID ownerId) {
                log.debug("Revoking all shares for file: fileId={}, ownerId={}", fileId, ownerId);

                File file = fileRepository.findByIdAndUserId(fileId, ownerId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found for owner %s", fileId,
                                                                ownerId)));

                List<FileShare> shares = shareRepository.findByFileId(fileId);
                shareRepository.deleteAll(shares);

                log.info("All shares revoked for file: fileId={}, count={}", fileId, shares.size());

                // Уведомляем всех пользователей
                for (FileShare share : shares) {
                        java.util.Map<String, String> metadata = new java.util.HashMap<>();
                        metadata.put("fileName", file.getName());
                        metadata.put("ownerId", ownerId.toString());

                        eventPublisher.publish(FileEvent.builder()
                                        .eventId(UUID.randomUUID().toString())
                                        .eventType("file.unshared")
                                        .fileId(fileId)
                                        .userId(share.getSharedWithUserId())
                                        .timestamp(LocalDateTime.now())
                                        .version(1)
                                        .metadata(metadata)
                                        .build());
                }
        }

        /**
         * Получение списка расшаренных файлов для пользователя
         */
        @Transactional(readOnly = true)
        public Page<FileShare> getSharedFiles(UUID userId, Pageable pageable) {
                log.debug("Getting shared files for user: userId={}, page={}, size={}",
                                userId, pageable.getPageNumber(), pageable.getPageSize());

                // First, get IDs with pagination
                Page<UUID> shareIdsPage = shareRepository.findActiveShareIdsForUser(userId, LocalDateTime.now(),
                                pageable);

                if (shareIdsPage.isEmpty()) {
                        return Page.empty(pageable);
                }

                // Then fetch full entities with files
                List<FileShare> shares = shareRepository.findByIdInWithFile(shareIdsPage.getContent());

                // Return as Page
                return new org.springframework.data.domain.PageImpl<>(shares, pageable,
                                shareIdsPage.getTotalElements());
        }

        /**
         * Получение списка расшариваний для файла
         */
        @Transactional(readOnly = true)
        public List<FileShare> getFileShares(UUID fileId, UUID ownerId) {
                log.debug("Getting shares for file: fileId={}, ownerId={}", fileId, ownerId);

                fileRepository.findByIdAndUserId(fileId, ownerId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found for owner %s", fileId,
                                                                ownerId)));

                return shareRepository.findActiveSharesByFileId(fileId, LocalDateTime.now());
        }

        /**
         * Получение всех расшариваний для файла (включая истекшие)
         */
        @Transactional(readOnly = true)
        public List<FileShare> getAllFileShares(UUID fileId) {
                log.debug("Getting all shares for file: fileId={}", fileId);
                return shareRepository.findByFileId(fileId);
        }

        /**
         * Проверка наличия активного расшаривания
         */
        @Transactional(readOnly = true)
        public boolean hasActiveShare(UUID fileId, UUID userId) {
                return shareRepository.existsActiveShare(fileId, userId, LocalDateTime.now());
        }

        /**
         * Получение расшаривания
         */
        @Transactional(readOnly = true)
        public Optional<FileShare> getShare(UUID fileId, UUID sharedWithUserId) {
                return shareRepository.findByFileIdAndSharedWithUserId(fileId, sharedWithUserId);
        }

        /**
         * Обновление прав доступа в расшаривании
         */
        public FileShare updateSharePermission(UUID fileId, UUID ownerId, UUID sharedWithUserId,
                        SharePermission newPermission) {
                log.debug("Updating share permission: fileId={}, sharedWithUserId={}, newPermission={}",
                                fileId, sharedWithUserId, newPermission);

                FileShare share = shareRepository.findByFileIdAndSharedWithUserId(fileId, sharedWithUserId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("Share not found for file %s and user %s",
                                                                fileId, sharedWithUserId)));

                if (!share.getOwnerId().equals(ownerId)) {
                        throw new IllegalArgumentException("Only file owner can update share permissions");
                }

                share.setPermission(newPermission);
                FileShare updatedShare = shareRepository.save(share);
                log.info("Share permission updated: id={}, newPermission={}",
                                updatedShare.getId(), newPermission);
                return updatedShare;
        }

        /**
         * Удаление истекших расшариваний
         */
        public void cleanupExpiredShares() {
                log.debug("Cleaning up expired shares");
                LocalDateTime now = LocalDateTime.now();
                List<FileShare> expiredShares = shareRepository.findExpiredShares(now);

                if (!expiredShares.isEmpty()) {
                        shareRepository.deleteAll(expiredShares);
                        log.info("Deleted {} expired shares", expiredShares.size());
                }
        }

        /**
         * Подсчет активных расшариваний для файла
         */
        @Transactional(readOnly = true)
        public long countActiveShares(UUID fileId) {
                return shareRepository.countActiveShares(fileId, LocalDateTime.now());
        }

        /**
         * Список файлов, расшаренных со мной (для RPC ListSharedWithMe)
         */
        @Transactional(readOnly = true)
        public List<FileShare> listSharedWithMe(UUID userId) {
                log.debug("Listing files shared with user: userId={}", userId);

                // Get all share IDs (unpaged)
                Page<UUID> shareIdsPage = shareRepository.findActiveShareIdsForUser(userId, LocalDateTime.now(),
                                Pageable.unpaged());

                if (shareIdsPage.isEmpty()) {
                        return List.of();
                }

                // Fetch full entities with files
                return shareRepository.findByIdInWithFile(shareIdsPage.getContent());
        }

        /**
         * Список моих shares (для RPC ListMyShares)
         * Возвращает все shares для файлов, принадлежащих пользователю
         */
        @Transactional(readOnly = true)
        public List<FileShare> listMyShares(UUID ownerId, UUID fileId) {
                log.debug("Listing my shares: ownerId={}, fileId={}", ownerId, fileId);

                if (fileId != null) {
                        // Shares для конкретного файла
                        File file = fileRepository.findByIdAndUserId(fileId, ownerId)
                                        .filter(f -> !f.isDeleted())
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                        String.format("File with id %s not found for owner %s", fileId,
                                                                        ownerId)));

                        return shareRepository.findActiveSharesByFileId(fileId, LocalDateTime.now());
                } else {
                        // Все shares для всех файлов пользователя
                        List<File> userFiles = fileRepository.findByUserIdAndIsDeletedFalse(ownerId, Pageable.unpaged())
                                        .getContent();
                        List<UUID> fileIds = userFiles.stream().map(File::getId).toList();

                        return shareRepository.findByFileIdIn(fileIds).stream()
                                        .filter(share -> share.getExpiresAt() == null
                                                        || share.getExpiresAt().isAfter(LocalDateTime.now()))
                                        .toList();
                }
        }

        /**
         * Отзыв share по ID (для RPC RevokeShare)
         */
        public void revokeShareById(UUID shareId, UUID ownerId) {
                log.debug("Revoking share by ID: shareId={}, ownerId={}", shareId, ownerId);

                FileShare share = shareRepository.findById(shareId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("Share with id %s not found", shareId)));

                // Проверка, что отзыв делает владелец файла ИЛИ сам пользователь, которому
                // расшарили
                if (!share.getOwnerId().equals(ownerId) && !share.getSharedWithUserId().equals(ownerId)) {
                        throw new IllegalArgumentException(
                                        "Only file owner or the recipient can revoke shares");
                }

                shareRepository.delete(share);
                log.info("Share revoked: shareId={}, fileId={}, sharedWithUserId={}",
                                shareId, share.getFile().getId(), share.getSharedWithUserId());

                eventPublisher.publish(FileEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("file.share.revoked")
                                .fileId(share.getFile().getId())
                                .userId(ownerId)
                                .timestamp(LocalDateTime.now())
                                .payload(share)
                                .build());
        }

        /**
         * Получение контекста доступа к файлу (для RPC GetFileAccessContext)
         * Возвращает информацию о правах доступа пользователя к файлу
         */
        @Transactional(readOnly = true)
        public FileAccessContext getFileAccessContext(UUID fileId, UUID userId) {
                log.debug("Getting file access context: fileId={}, userId={}", fileId, userId);

                File file = fileRepository.findById(fileId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found", fileId)));

                FileAccessContext context = new FileAccessContext();
                context.setFileId(fileId);
                context.setUserId(userId);

                // Проверяем, является ли пользователь владельцем
                if (file.getUserId().equals(userId)) {
                        context.setAccessType(AccessType.OWNER);
                        context.setPermission("ALL");
                        context.setCanRead(true);
                        context.setCanWrite(true);
                        context.setCanDelete(true);
                        context.setCanShare(true);

                        // Добавляем список существующих shares (только для владельца)
                        List<FileShare> shares = shareRepository.findActiveSharesByFileId(fileId,
                                        LocalDateTime.now());
                        context.setExistingShares(shares);
                } else {
                        // Проверяем, есть ли share
                        Optional<FileShare> shareOpt = shareRepository.findByFileIdAndSharedWithUserId(fileId, userId);

                        if (shareOpt.isPresent() && shareOpt.get().isActive()) {
                                FileShare share = shareOpt.get();

                                // Проверяем, не истек ли share
                                if (share.getExpiresAt() != null
                                                && share.getExpiresAt().isBefore(LocalDateTime.now())) {
                                        // Share истек
                                        context.setAccessType(AccessType.NONE);
                                        context.setPermission("");
                                        context.setCanRead(false);
                                        context.setCanWrite(false);
                                        context.setCanDelete(false);
                                        context.setCanShare(false);
                                } else {
                                        // Share активен
                                        context.setAccessType(AccessType.SHARED);
                                        context.setPermission(share.getPermission().name());
                                        context.setCanRead(share.hasReadPermission());
                                        context.setCanWrite(share.hasWritePermission());
                                        context.setCanDelete(false); // Shared user никогда не может удалить
                                        context.setCanShare(false); // Shared user не может расшаривать
                                }
                        } else {
                                // Нет доступа
                                context.setAccessType(AccessType.NONE);
                                context.setPermission("");
                                context.setCanRead(false);
                                context.setCanWrite(false);
                                context.setCanDelete(false);
                                context.setCanShare(false);
                        }
                }

                return context;
        }

        /**
         * Проверка прав доступа (внутренний метод для использования в других сервисах)
         * 
         * @throws IllegalArgumentException если нет доступа
         */
        public void checkSharePermission(UUID fileId, UUID userId, SharePermission requiredPermission) {
                log.debug("Checking share permission: fileId={}, userId={}, requiredPermission={}",
                                fileId, userId, requiredPermission);

                File file = fileRepository.findById(fileId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found", fileId)));

                // Владелец имеет все права
                if (file.getUserId().equals(userId)) {
                        return;
                }

                // Проверяем share
                FileShare share = shareRepository.findByFileIdAndSharedWithUserId(fileId, userId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("Access denied: no share found for file %s and user %s",
                                                                fileId, userId)));

                // Проверяем активность
                if (!share.isActive()) {
                        throw new IllegalArgumentException("Share is not active");
                }

                // Проверяем срок действия
                if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw new IllegalArgumentException("Share has expired");
                }

                // Проверяем права
                if (requiredPermission == SharePermission.WRITE && !share.hasWritePermission()) {
                        throw new IllegalArgumentException(
                                        String.format("Write permission required, but user has only %s",
                                                        share.getPermission()));
                }
        }

        /**
         * Enum для типа доступа
         */
        public enum AccessType {
                NONE, OWNER, SHARED
        }

        /**
         * Класс для хранения контекста доступа к файлу
         */
        @lombok.Data
        public static class FileAccessContext {
                private UUID fileId;
                private UUID userId;
                private AccessType accessType;
                private String permission;
                private boolean canRead;
                private boolean canWrite;
                private boolean canDelete;
                private boolean canShare;
                private List<FileShare> existingShares;
        }
}
