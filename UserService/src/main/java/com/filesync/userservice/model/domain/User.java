package com.filesync.userservice.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private UUID id; // ID comes from Auth Service usually

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "storage_used", nullable = false)
    @ColumnDefault("0")
    private Long storageUsed;

    @Column(name = "storage_quota", nullable = false)
    @ColumnDefault("5368709120") // 5GB
    private Long storageQuota;

    @Column(name = "plan")
    @Builder.Default
    private String plan = "free";

    @Column(name = "max_file_size")
    @Builder.Default
    private Long maxFileSize = 104857600L; // 100MB

    @Column(name = "max_devices")
    @Builder.Default
    private Integer maxDevices = 3;

    @Column(name = "max_shares")
    @Builder.Default
    private Integer maxShares = 10;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "blocked_by")
    private UUID blockedBy;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // Transient field for roles from AuthService (not stored in DB)
    @Transient
    private java.util.List<String> roles;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
