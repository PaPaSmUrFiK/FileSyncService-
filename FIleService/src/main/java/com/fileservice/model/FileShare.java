package com.fileservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Расшаривание файлов
 */
@Entity
@Table(name = "file_shares", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_shared_user", columnNames = { "file_id", "shared_with_user_id" })
}, indexes = {
        @Index(name = "idx_file_shares_file_id", columnList = "file_id"),
        @Index(name = "idx_file_shares_shared_with", columnList = "shared_with_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Column(name = "shared_with_user_id", nullable = false)
    private UUID sharedWithUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharePermission permission;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * User ID who created this share (typically the file owner)
     * Used for audit purposes
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * Expiration date for the share (optional)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Whether the share is active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    // Helper methods
    public boolean hasReadPermission() {
        return permission.canRead();
    }

    public boolean hasWritePermission() {
        return permission.canWrite();
    }

    public boolean hasAdminPermission() {
        return permission.canAdmin();
    }

    /**
     * Get owner ID from the associated file
     * Source of truth is file.userId, not duplicated here
     */
    public UUID getOwnerId() {
        return file != null ? file.getUserId() : null;
    }
}
