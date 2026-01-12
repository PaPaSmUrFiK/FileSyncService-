package com.fileservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Основная сущность файла/папки
 */
@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_user_id", columnList = "user_id"),
        @Index(name = "idx_files_path", columnList = "path"),
        @Index(name = "idx_files_parent_folder", columnList = "parent_folder_id"),
        @Index(name = "idx_files_hash", columnList = "hash"),
        @Index(name = "idx_files_is_deleted", columnList = "is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 1000)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private File parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL)
    @Builder.Default
    private List<File> children = new ArrayList<>();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    @Builder.Default
    private Long size = 0L;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(length = 64)
    private String hash;

    @Column(name = "is_folder", nullable = false)
    @Builder.Default
    private Boolean isFolder = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("version DESC")
    @Builder.Default
    private List<FileVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileShare> shares = new ArrayList<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FilePermission> permissions = new ArrayList<>();

    public boolean isFolder() {
        return Boolean.TRUE.equals(isFolder);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(isDeleted);
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }

    public void incrementVersion() {
        this.version++;
    }

    @Transient
    private String uploadUrl;

    @Transient
    private String downloadUrl;

    @PrePersist
    protected void onCreate() {
        if (createdBy == null) {
            createdBy = userId;
        }
    }
}
