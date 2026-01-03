package com.fileservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Детальные права доступа к файлам
 */
@Entity
@Table(name = "file_permissions", indexes = {
        @Index(name = "idx_file_permissions_file_id", columnList = "file_id"),
        @Index(name = "idx_file_permissions_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionType permission;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum PermissionType {
        READ, WRITE, DELETE, SHARE, ADMIN;

        public int getLevel() {
            return switch (this) {
                case READ -> 1;
                case WRITE -> 2;
                case DELETE -> 3;
                case SHARE -> 4;
                case ADMIN -> 5;
            };
        }

        public boolean includes(PermissionType other) {
            return this.getLevel() >= other.getLevel();
        }
    }

    public boolean allowsRead() {
        return permission.includes(PermissionType.READ);
    }

    public boolean allowsWrite() {
        return permission.includes(PermissionType.WRITE);
    }

    public boolean allowsDelete() {
        return permission.includes(PermissionType.DELETE);
    }
}
