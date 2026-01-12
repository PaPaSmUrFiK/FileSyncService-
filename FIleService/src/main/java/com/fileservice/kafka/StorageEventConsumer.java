package com.fileservice.kafka;

import com.fileservice.model.File;
import com.fileservice.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StorageEventConsumer {

    private final FileRepository fileRepository;

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
                }
            });
        } catch (IllegalArgumentException e) {
            log.error("Invalid file ID in storage event: {}", event.getFileId());
        } catch (Exception e) {
            log.error("Error processing stored event for file {}", event.getFileId(), e);
        }
    }
}
