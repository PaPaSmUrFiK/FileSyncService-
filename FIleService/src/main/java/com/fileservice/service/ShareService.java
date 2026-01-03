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
                                .ownerId(ownerId)
                                .sharedWithUserId(sharedWithUserId)
                                .permission(permission)
                                .expiresAt(expiresAt)
                                .build();

                FileShare savedShare = shareRepository.save(share);
                log.info("File shared: id={}, fileId={}, ownerId={}, sharedWithUserId={}, permission={}",
                                savedShare.getId(), fileId, ownerId, sharedWithUserId, permission);

                eventPublisher.publish(FileEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("file.shared")
                                .fileId(fileId)
                                .userId(ownerId)
                                .timestamp(LocalDateTime.now())
                                .version(1)
                                .payload(savedShare)
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

                shareRepository.delete(share);
                log.info("Share revoked: fileId={}, sharedWithUserId={}", fileId, sharedWithUserId);
        }

        /**
         * Отзыв всех расшариваний для файла
         */
        public void revokeAllShares(UUID fileId, UUID ownerId) {
                log.debug("Revoking all shares for file: fileId={}, ownerId={}", fileId, ownerId);

                fileRepository.findByIdAndUserId(fileId, ownerId)
                                .filter(f -> !f.isDeleted())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                String.format("File with id %s not found for owner %s", fileId,
                                                                ownerId)));

                List<FileShare> shares = shareRepository.findByFileId(fileId);
                shareRepository.deleteAll(shares);

                log.info("All shares revoked for file: fileId={}, count={}", fileId, shares.size());
        }

        /**
         * Получение списка расшаренных файлов для пользователя
         */
        @Transactional(readOnly = true)
        public Page<FileShare> getSharedFiles(UUID userId, Pageable pageable) {
                log.debug("Getting shared files for user: userId={}, page={}, size={}",
                                userId, pageable.getPageNumber(), pageable.getPageSize());
                return shareRepository.findActiveSharesForUser(userId, LocalDateTime.now(), pageable);
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
}
