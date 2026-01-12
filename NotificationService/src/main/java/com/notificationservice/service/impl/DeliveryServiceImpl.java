package com.notificationservice.service.impl;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.model.domain.NotificationPreference;
import com.notificationservice.repository.NotificationPreferenceRepository;
import com.notificationservice.service.DeliveryService;
import com.notificationservice.service.EmailService;
import com.notificationservice.service.PushService;
import com.notificationservice.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryServiceImpl implements DeliveryService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final WebSocketService webSocketService;
    private final EmailService emailService;
    private final PushService pushService;

    @Override
    public Mono<Void> deliver(Notification notification, List<String> requestedChannels) {
        return preferenceRepository.findByUserId(notification.getUserId())
                .flatMap(preference -> {
                    Mono<Void> deliveryChain = Mono.empty();

                    // WebSocket delivery
                    if (shouldDeliver(notification, preference, "websocket", requestedChannels)) {
                        deliveryChain = deliveryChain
                                .then(webSocketService.sendNotification(notification.getUserId(), notification));
                    }

                    // Push delivery
                    if (shouldDeliver(notification, preference, "push", requestedChannels)) {
                        deliveryChain = deliveryChain
                                .then(pushService.sendPush(notification.getUserId(), notification));
                    }

                    // Email delivery
                    if (shouldDeliver(notification, preference, "email", requestedChannels)) {
                        deliveryChain = deliveryChain
                                .then(emailService.sendEmail(notification.getUserId(), notification));
                    }

                    return deliveryChain;
                })
                .doOnError(e -> log.error("Delivery failed for notification {}: {}", notification.getId(),
                        e.getMessage()));
    }

    private boolean shouldDeliver(Notification notification, NotificationPreference preference, String channel,
            List<String> requestedChannels) {
        // Urgent priority bypasses settings and quiet hours
        if ("urgent".equalsIgnoreCase(notification.getPriority())) {
            return true;
        }

        // Check if channel is enabled globally for user
        boolean channelEnabled = switch (channel) {
            case "websocket" -> preference.isWebsocketEnabled();
            case "push" -> preference.isPushEnabled();
            case "email" -> preference.isEmailEnabled();
            default -> false;
        };

        if (!channelEnabled)
            return false;

        // Check if type of notification is enabled for user
        boolean typeEnabled = switch (notification.getNotificationType()) {
            case "FILE_UPLOADED", "FILE_DELETED" -> preference.isFileNotifications();
            case "FILE_SHARED" -> preference.isShareNotifications();
            case "SYNC_COMPLETED", "SYNC_FAILED", "CONFLICT_DETECTED" -> preference.isSyncNotifications();
            case "USER_BLOCKED", "USER_UNBLOCKED", "QUOTA_CHANGED", "PLAN_CHANGED", "ROLE_ASSIGNED", "ROLE_REVOKED" ->
                preference.isAdminNotifications();
            case "STORAGE_QUOTA_WARNING", "SYSTEM_ANNOUNCEMENT" -> preference.isSystemNotifications();
            default -> true;
        };

        if (!typeEnabled)
            return false;

        // Check requested channels if specified
        if (requestedChannels != null && !requestedChannels.isEmpty()) {
            if (!requestedChannels.contains(channel))
                return false;
        } else {
            // Apply priority logic if no specific channels requested
            // low: only WebSocket
            // normal: WebSocket + Push
            // high: WebSocket + Push + Email
            if ("low".equalsIgnoreCase(notification.getPriority()) && !channel.equals("websocket"))
                return false;
            if ("normal".equalsIgnoreCase(notification.getPriority()) && channel.equals("email"))
                return false;
        }

        // Check Quiet Hours for Email and Push
        if (preference.isQuietHoursEnabled() && (channel.equals("email") || channel.equals("push"))) {
            LocalTime now = LocalTime.now();
            LocalTime start = preference.getQuietHoursStart();
            LocalTime end = preference.getQuietHoursEnd();
            if (start != null && end != null) {
                if (start.isBefore(end)) {
                    if (now.isAfter(start) && now.isBefore(end))
                        return false;
                } else { // Overnight quiet hours (e.g. 23:00 to 07:00)
                    if (now.isAfter(start) || now.isBefore(end))
                        return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean isDeliveryAllowed(Notification notification, NotificationPreference preference, String channel) {
        return shouldDeliver(notification, preference, channel, null);
    }
}
