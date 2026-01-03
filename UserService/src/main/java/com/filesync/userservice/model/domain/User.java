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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
