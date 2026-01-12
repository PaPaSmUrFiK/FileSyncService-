package com.notificationservice.model.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("push_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushToken {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("device_id")
    private UUID deviceId;

    private String token;
    private String platform; // fcm, apns

    @Column("is_active")
    private boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("last_used_at")
    private LocalDateTime lastUsedAt;
}
