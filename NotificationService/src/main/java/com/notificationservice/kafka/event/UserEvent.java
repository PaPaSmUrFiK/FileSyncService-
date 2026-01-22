package com.notificationservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private String eventId;
    private String eventType; // user.blocked, user.unblocked, user.role_changed, user.password_changed
    private UUID userId;

    private String timestamp;

    private Map<String, String> metadata; // reason, changedBy, role, action, etc.
    private Object payload;
}
