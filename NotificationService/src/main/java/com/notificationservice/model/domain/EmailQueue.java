package com.notificationservice.model.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import io.r2dbc.postgresql.codec.Json;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("email_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailQueue {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("to_email")
    private String toEmail;

    private String subject;
    private String body;

    @Column("template_name")
    private String templateName;

    @Column("template_data")
    private Json templateData;

    private String status; // pending, sending, sent, failed
    private int attempts;

    @Column("max_attempts")
    private int maxAttempts;

    @Column("last_error")
    private String lastError;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("sent_at")
    private LocalDateTime sentAt;
}
