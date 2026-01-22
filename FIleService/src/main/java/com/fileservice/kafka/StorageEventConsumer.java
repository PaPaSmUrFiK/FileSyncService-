package com.fileservice.kafka;

import com.fileservice.event.FileEvent;
import com.fileservice.model.File;
import com.fileservice.repository.FileRepository;
import com.fileservice.service.FileEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StorageEventConsumer {

    private final FileRepository fileRepository;
    private final com.fileservice.repository.FileShareRepository shareRepository;
    private final FileEventPublisher eventPublisher;

    @KafkaListener(topics = "${kafka.topics.storage-events:storage.events}", groupId = "${spring.kafka.consumer.group-id:file-service}")
    @Transactional
    public void handleStorageEvent(StorageEvent event) {
        log.info("Received storage event: {}", event);

        if ("stored".equals(event.getEventType())) {
            handleStoredEvent(event);
        }
    }

    private void handleStoredEvent(StorageEvent event) {
        try {
            UUID fileId = UUID.fromString(event.getFileId());
            fileRepository.findById(fileId).ifPresent(file -> {
                // Update size and hash from storage confirmation
                boolean updated = false;

                if (event.getSize() != null && event.getSize() > 0) {
                    file.setSize(event.getSize());
                    updated = true;
                }

                if (event.getHash() != null && !event.getHash().isEmpty()) {
                    file.setHash(event.getHash());
                    updated = true;
                }

                // We do NOT update storagePath or version here as they are managed by
                // FileService before upload.
                // However, we might want to verify them or update status if we had one.

                if (updated) {
                    fileRepository.save(file);
                    log.info("Updated file metadata from storage event: id={}, size={}, hash={}",
                            fileId, event.getSize(), event.getHash());

                    // Publish notification event
                    // If version is 1, it's a new file -> file.uploaded
                    // If version > 1, it's an update -> file.version_uploaded
                    String eventType = file.getVersion() == 1 ? "file.uploaded" : "file.version_uploaded";

                    eventPublisher.publish(FileEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType(eventType)
                            .fileId(file.getId())
                            .userId(file.getUserId())
                            .timestamp(LocalDateTime.now())
                            .version(file.getVersion())
                            .payload(file)
                            .metadata(Map.of(
                                    "fileName", file.getName(),
                                    "size", String.valueOf(file.getSize()),
                                    "version", String.valueOf(file.getVersion())))
                            .build());

                    // --- Notify shared users ---
                    try {
                        java.util.List<com.fileservice.model.FileShare> shares = shareRepository
                                .findByFileId(file.getId());
                        for (com.fileservice.model.FileShare share : shares) {
                            // Avoid double notification if sharedWith is the same as owner (shouldn't
                            // happen)
                            if (share.getSharedWithUserId().equals(file.getUserId()))
                                continue;

                            eventPublisher.publish(FileEvent.builder()
                                    .eventId(UUID.randomUUID().toString())
                                    .eventType(eventType)
                                    .fileId(file.getId())
                                    .userId(share.getSharedWithUserId()) // Target the recipient
                                    .timestamp(LocalDateTime.now())
                                    .version(file.getVersion())
                                    .payload(file)
                                    .metadata(Map.of(
                                            "fileName", file.getName(),
                                            "size", String.valueOf(file.getSize()),
                                            "version", String.valueOf(file.getVersion()),
                                            "ownerId", file.getUserId().toString(),
                                            "sharedBy", file.getUserId().toString() // Context for notification
                            ))
                                    .build());
                        }
                    } catch (Exception e) {
                        log.error("Failed to notify shared users regarding file upload: {}", file.getId(), e);
                    }
                    // ---------------------------

                }
            });
        } catch (IllegalArgumentException e) {
            log.error("Invalid file ID in storage event: {}", event.getFileId());
        } catch (Exception e) {
            log.error("Error processing stored event for file {}", event.getFileId(), e);
        }
    }
}
