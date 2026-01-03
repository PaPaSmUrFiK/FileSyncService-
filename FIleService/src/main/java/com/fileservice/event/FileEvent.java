package com.fileservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileEvent {
    private String eventId;
    private String eventType; // file.created, updated, etc.
    private UUID fileId;
    private UUID userId;
    private LocalDateTime timestamp;
    private Object payload; // Flexible payload (could be file metadata, version info, etc.)
    private int version; // Event schema version
}
