package com.notificationservice.model.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import io.r2dbc.postgresql.codec.Json;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("notification_type")
    private String notificationType;

    private String title;
    private String message;
    private String priority; // low, normal, high, urgent

    private Json data;

    @Column("is_read")
    private boolean read;

    @Column("read_at")
    private LocalDateTime readAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("expires_at")
    private LocalDateTime expiresAt;
}
