package com.notificationservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.kafka.event.FileEvent;
import com.notificationservice.kafka.event.UserEvent;
import com.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

        private final NotificationService notificationService;
        private final ObjectMapper objectMapper;

        @KafkaListener(topics = "file-events", groupId = "notification-service-group")
        public void handleFileEvent(String eventJson) {
                try {
                        FileEvent event = objectMapper.readValue(eventJson, FileEvent.class);
                        log.debug("Received file event: {}", event);
                        processFileEvent(event);
                } catch (JsonProcessingException e) {
                        log.error("Error parsing file event: {}", eventJson, e);
                }
        }

        @KafkaListener(topics = "user-events", groupId = "notification-service-group")
        public void handleUserEvent(String eventJson) {
                try {
                        UserEvent event = objectMapper.readValue(eventJson, UserEvent.class);
                        log.debug("Received user event: {}", event);
                        processUserEvent(event);
                } catch (JsonProcessingException e) {
                        log.error("Error parsing user event: {}", eventJson, e);
                }
        }

        private void processFileEvent(FileEvent event) {
                String type = event.getEventType();
                UUID ownerId = event.getUserId();
                String fileName = event.getMetadata().getOrDefault("fileName", "Unknown File");
                String oldName = event.getMetadata().get("oldName");

                switch (type) {
                        case "file.uploaded" -> notificationService.sendNotification(ownerId, "FILE_UPLOADED",
                                        "Файл загружен", "Файл " + fileName + " успешно загружен", "normal",
                                        event.getFileId(), "FILE", event.getMetadata(), null)
                                        .subscribe();

                        case "file.version_uploaded" ->
                                notificationService.sendNotification(ownerId, "FILE_VERSION_UPLOADED",
                                                "Новая версия", "Загружена новая версия файла " + fileName, "normal",
                                                event.getFileId(), "FILE", event.getMetadata(), null)
                                                .subscribe();

                        case "file.renamed" -> {
                                String msg = String.format("Файл %s был переименован в %s",
                                                oldName != null ? oldName : "Unknown", fileName);
                                notificationService.sendNotification(ownerId, "FILE_RENAMED",
                                                "Файл переименован", msg, "normal",
                                                event.getFileId(), "FILE", event.getMetadata(), null)
                                                .subscribe();
                        }

                        case "file.shared" -> {
                                String sharedWithIdStr = event.getMetadata().get("sharedWithUserId");
                                if (sharedWithIdStr != null) {
                                        try {
                                                UUID sharedWithId = UUID.fromString(sharedWithIdStr);
                                                notificationService
                                                                .exists(sharedWithId, event.getFileId(), "FILE_SHARED")
                                                                .flatMap(exists -> {
                                                                        if (Boolean.TRUE.equals(exists)) {
                                                                                log.debug("Notification for shared file {} to user {} already exists",
                                                                                                event.getFileId(),
                                                                                                sharedWithId);
                                                                                return Mono.empty();
                                                                        }
                                                                        return notificationService.sendNotification(
                                                                                        sharedWithId, "FILE_SHARED",
                                                                                        "Доступ к файлу",
                                                                                        "Вам открыли доступ к файлу "
                                                                                                        + fileName,
                                                                                        "high",
                                                                                        event.getFileId(), "FILE",
                                                                                        event.getMetadata(), null);
                                                                })
                                                                .subscribe();
                                        } catch (IllegalArgumentException e) {
                                                log.error("Invalid sharedWithUserId: {}", sharedWithIdStr);
                                        }
                                }
                        }

                        case "file.deleted" -> notificationService.sendNotification(ownerId, "FILE_DELETED",
                                        "Файл перемещен в корзину", "Файл " + fileName + " был перемещен в корзину",
                                        "normal",
                                        event.getFileId(), "FILE", event.getMetadata(), null)
                                        .subscribe();

                        case "file.hard_deleted" -> notificationService
                                        .sendNotification(ownerId, "FILE_PERMANENTLY_DELETED",
                                                        "Файл удален навсегда",
                                                        "Файл " + fileName + " был удален навсегда", "normal",
                                                        event.getFileId(), "FILE", event.getMetadata(), null)
                                        .subscribe();

                        case "file.unshared" -> notificationService.sendNotification(ownerId, "FILE_UNSHARED",
                                        "Доступ отозван",
                                        "Доступ к файлу " + fileName + " был отозван", "normal",
                                        event.getFileId(), "FILE", event.getMetadata(), null)
                                        .subscribe();

                        case "file.restored" -> notificationService.sendNotification(ownerId, "FILE_RESTORED",
                                        "Файл восстановлен",
                                        "Файл " + fileName + " был восстановлен из корзины", "normal",
                                        event.getFileId(), "FILE", event.getMetadata(), null)
                                        .subscribe();
                }
        }

        private void processUserEvent(UserEvent event) {
                String type = event.getEventType();
                UUID userId = event.getUserId();
                Map<String, String> meta = event.getMetadata() != null ? event.getMetadata() : Collections.emptyMap();

                switch (type) {
                        case "user.blocked" -> {
                                String reason = meta.getOrDefault("reason", "No reason provided");
                                notificationService.sendNotification(userId, "USER_BLOCKED",
                                                "Аккаунт заблокирован", "Ваш аккаунт заблокирован. Причина: " + reason,
                                                "urgent",
                                                userId, "USER", meta, null)
                                                .subscribe();
                        }
                        case "user.unblocked" -> notificationService.sendNotification(userId, "USER_UNBLOCKED",
                                        "Аккаунт разблокирован", "Ваш аккаунт был разблокирован администратором",
                                        "high",
                                        userId, "USER", meta, null)
                                        .subscribe();

                        case "user.role_changed" -> {
                                String action = meta.getOrDefault("action", "changed");
                                String role = meta.getOrDefault("role", "Unknown");
                                String title = "Роль " + ("assigned".equals(action) ? "назначена" : "отозвана");
                                String msg = "Роль " + role + " была "
                                                + ("assigned".equals(action) ? "назначена вам" : "отозвана у вас");

                                notificationService.sendNotification(userId, "USER_ROLE_CHANGED",
                                                title, msg, "high",
                                                userId, "USER", meta, null)
                                                .subscribe();
                        }

                        case "user.role_assigned" -> {
                                String role = meta.getOrDefault("role", "Admin");
                                notificationService.sendNotification(userId, "USER_ROLE_ASSIGNED",
                                                "Роль назначена", "Вам назначена новая роль: " + role, "high",
                                                userId, "USER", meta, null)
                                                .subscribe();
                        }

                        case "user.role_revoked" -> {
                                String role = meta.getOrDefault("role", "Admin");
                                notificationService.sendNotification(userId, "USER_ROLE_REVOKED",
                                                "Роль отозвана", "У вас отозвана роль: " + role, "high",
                                                userId, "USER", meta, null)
                                                .subscribe();
                        }

                        case "user.password_changed" ->
                                notificationService.sendNotification(userId, "USER_PASSWORD_CHANGED",
                                                "Пароль изменен", "Пароль вашего аккаунта был успешно изменен", "high",
                                                userId, "USER", meta, null)
                                                .subscribe();
                }
        }
}
