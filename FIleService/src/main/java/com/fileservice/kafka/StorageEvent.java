package com.fileservice.kafka;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageEvent {

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("storage_path")
    private String storagePath;

    @JsonProperty("bucket")
    private String bucket;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("hash")
    private String hash;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}
