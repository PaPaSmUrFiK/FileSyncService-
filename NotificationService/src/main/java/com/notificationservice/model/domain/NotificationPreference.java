package com.notificationservice.model.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalTime;
import java.util.UUID;
import java.time.LocalDateTime;

@Table("notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("email_enabled")
    private boolean emailEnabled;

    @Column("push_enabled")
    private boolean pushEnabled;

    @Column("websocket_enabled")
    private boolean websocketEnabled;

    @Column("file_notifications")
    private boolean fileNotifications;

    @Column("sync_notifications")
    private boolean syncNotifications;

    @Column("share_notifications")
    private boolean shareNotifications;

    @Column("admin_notifications")
    private boolean adminNotifications;

    @Column("system_notifications")
    private boolean systemNotifications;

    @Column("quiet_hours_enabled")
    private boolean quietHoursEnabled;

    @Column("quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column("quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
