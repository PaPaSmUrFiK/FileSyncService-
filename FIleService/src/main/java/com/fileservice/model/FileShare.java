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

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "shared_with_user_id", nullable = false)
    private UUID sharedWithUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharePermission permission;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public boolean isActive() {
        return expiresAt == null || expiresAt.isAfter(LocalDateTime.now());
    }

    public boolean hasReadPermission() {
        return isActive() && permission.canRead();
    }

    public boolean hasWritePermission() {
        return isActive() && permission.canWrite();
    }

    public boolean hasAdminPermission() {
        return isActive() && permission.canAdmin();
    }
}
