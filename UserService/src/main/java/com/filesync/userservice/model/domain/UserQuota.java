package com.filesync.userservice.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_quotas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuota {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "plan_type", nullable = false)
    @ColumnDefault("'free'")
    private String planType;

    @Column(name = "max_file_size", nullable = false)
    @ColumnDefault("104857600") // 100MB
    private Long maxFileSize;

    @Column(name = "max_devices", nullable = false)
    @ColumnDefault("3")
    private Integer maxDevices;

    @Column(name = "max_shares", nullable = false)
    @ColumnDefault("10")
    private Integer maxShares;

    @Column(name = "version_history_days", nullable = false)
    @ColumnDefault("30")
    private Integer versionHistoryDays;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.validFrom == null) {
            this.validFrom = LocalDateTime.now();
        }
    }
}
