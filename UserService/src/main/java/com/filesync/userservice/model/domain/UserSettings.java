package com.filesync.userservice.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @ColumnDefault("'light'")
    private String theme;

    @Column(nullable = false)
    @ColumnDefault("'en'")
    private String language;

    @Column(name = "notifications_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean notificationsEnabled;

    @Column(name = "email_notifications", nullable = false)
    @ColumnDefault("true")
    private Boolean emailNotifications;

    @Column(name = "auto_sync", nullable = false)
    @ColumnDefault("true")
    private Boolean autoSync;

    @Column(name = "sync_on_mobile_data", nullable = false)
    @ColumnDefault("false")
    private Boolean syncOnMobileData;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
