package com.notificationservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEvent {
    private String eventId;
    private String eventType; // file.created, file.renamed, file.version_uploaded, file.deleted, file.shared
    private UUID fileId;
    private UUID userId;
    private String timestamp;
    private Integer version;
    private Map<String, String> metadata; // oldName, newName, sharedWithUserId, etc.
    private Object payload;
}
