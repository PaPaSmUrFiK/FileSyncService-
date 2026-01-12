package com.notificationservice.kafka;

import com.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "file.events", groupId = "notification-service-group")
    public void handleFileEvent(Map<String, Object> event) {
        log.debug("Received file event: {}", event);
        String type = (String) event.get("type");
        UUID userId = UUID.fromString((String) event.get("userId"));
        String fileName = (String) event.get("fileName");

        switch (type) {
            case "created" -> notificationService.sendNotification(userId, "FILE_UPLOADED",
                    "File synchronized", fileName + " has been synced", "normal", Map.of("fileName", fileName), null)
                    .subscribe();
            case "shared" -> {
                UUID sharedWith = UUID.fromString((String) event.get("sharedWithUserId"));
                notificationService.sendNotification(sharedWith, "FILE_SHARED",
                        "New file shared", "A file has been shared with you", "high", Map.of("fileName", fileName),
                        null).subscribe();
            }
            case "deleted" -> notificationService.sendNotification(userId, "FILE_DELETED",
                    "File deleted", fileName + " has been removed", "normal", Map.of("fileName", fileName), null)
                    .subscribe();
        }
    }

    @KafkaListener(topics = "sync.events", groupId = "notification-service-group")
    public void handleSyncEvent(Map<String, Object> event) {
        log.debug("Received sync event: {}", event);
        String type = (String) event.get("type");
        UUID userId = UUID.fromString((String) event.get("userId"));

        switch (type) {
            case "sync.completed" -> notificationService.sendNotification(userId, "SYNC_COMPLETED",
                    "Sync complete", "All your files are up to date", "low", null, null).subscribe();
            case "conflict.detected" -> notificationService.sendNotification(userId, "CONFLICT_DETECTED",
                    "File conflict", "Conflict detected in some files", "high", null, null).subscribe();
            case "sync.failed" -> notificationService.sendNotification(userId, "SYNC_FAILED",
                    "Sync failed", "Could not synchronize some files", "normal", null, null).subscribe();
        }
    }

    @KafkaListener(topics = "admin.events", groupId = "notification-service-group")
    public void handleAdminEvent(Map<String, Object> event) {
        log.debug("Received admin event: {}", event);
        String type = (String) event.get("type");
        UUID targetUserId = UUID.fromString((String) event.get("targetUserId"));

        switch (type) {
            case "user.blocked" -> notificationService.sendNotification(targetUserId, "USER_BLOCKED",
                    "Account blocked", "Your account has been blocked by administrator", "urgent", null, null)
                    .subscribe();
            case "user.unblocked" -> notificationService.sendNotification(targetUserId, "USER_UNBLOCKED",
                    "Account unblocked", "Your account has been unblocked", "high", null, null)
                    .subscribe();
            case "quota.changed" -> notificationService.sendNotification(targetUserId, "QUOTA_CHANGED",
                    "Storage quota updated", "Your storage quota has been changed", "high", null, null).subscribe();
            case "plan.changed" -> notificationService.sendNotification(targetUserId, "PLAN_CHANGED",
                    "Subscription plan updated", "Your subscription plan has been changed", "high", null, null)
                    .subscribe();
            case "role.assigned" -> notificationService.sendNotification(targetUserId, "ROLE_ASSIGNED",
                    "New role assigned", "A new role has been assigned to your account", "normal", null, null)
                    .subscribe();
            case "role.revoked" -> notificationService.sendNotification(targetUserId, "ROLE_REVOKED",
                    "Role revoked", "A role has been revoked from your account", "normal", null, null).subscribe();
        }
    }

    @KafkaListener(topics = "user.events", groupId = "notification-service-group")
    public void handleUserEvent(Map<String, Object> event) {
        log.debug("Received user event: {}", event);
        String type = (String) event.get("type");
        if ("user.registered".equals(type)) {
            UUID userId = UUID.fromString((String) event.get("userId"));
            String email = (String) event.get("email");
            log.info("Processing welcome email for user {}", userId);
            // Could trigger a special notification type for welcome email
        }

        if ("storage.updated".equals(type)) {
            UUID userId = UUID.fromString((String) event.get("userId"));
            double percentage = (double) event.get("storagePercentage");
            if (percentage >= 90.0) {
                notificationService.sendNotification(userId, "STORAGE_QUOTA_WARNING",
                        "Storage almost full", "Your storage is " + String.format("%.1f", percentage) + "% full",
                        "high",
                        Map.of("percentage", String.valueOf(percentage)), null).subscribe();
            }
        }
    }
}
