package com.fileservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Версии файлов
 */
@Entity
@Table(name = "file_versions", uniqueConstraints = {
                @UniqueConstraint(name = "uk_file_version", columnNames = { "file_id", "version" })
}, indexes = {
                @Index(name = "idx_file_versions_file_id", columnList = "file_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVersion {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "file_id", nullable = false)
        private File file;

        @Column(nullable = false)
        private Integer version;

        @Column(nullable = false)
        private Long size;

        @Column(nullable = false, length = 64)
        private String hash;

        @Column(name = "storage_path", nullable = false, length = 500)
        private String storagePath;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "created_by", nullable = false)
        private UUID createdBy;

        public boolean isLatestVersion() {
                return file != null && file.getVersion().equals(this.version);
        }
}
