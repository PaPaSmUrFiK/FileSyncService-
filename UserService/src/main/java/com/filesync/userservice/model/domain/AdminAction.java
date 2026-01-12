package com.filesync.userservice.model.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    // Postgres JSONB support requires Hibernate 6+ and @JdbcTypeCode(SqlTypes.JSON)
    // for String/Map mapping
    // We are on Spring Boot 3.2 which uses Hibernate 6.
    @Column(name = "action_details")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actionDetails;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
