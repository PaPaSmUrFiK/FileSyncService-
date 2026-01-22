package com.gatewayservice.client;

import com.notificationservice.grpc.NotificationServiceGrpc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceClient {

    @GrpcClient("notification-service")
    private NotificationServiceGrpc.NotificationServiceBlockingStub notificationServiceStub;

    public Mono<Void> sendNotification(String userId, String notificationType, String title,
            String message, String priority, Map<String, String> data, List<String> channels) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.SendNotificationRequest.Builder builder = com.notificationservice.grpc.SendNotificationRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setNotificationType(notificationType)
                        .setTitle(title)
                        .setMessage(message)
                        .setPriority(priority != null ? priority : "normal");
                if (data != null) {
                    builder.putAllData(data);
                }
                if (channels != null) {
                    builder.addAllChannels(channels);
                }
                notificationServiceStub.sendNotification(builder.build());
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error sending notification: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to send notification");
            } catch (Exception e) {
                log.error("Unexpected error sending notification: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }

    public Mono<com.notificationservice.grpc.BulkSendResponse> sendBulkNotifications(
            List<String> userIds, String notificationType, String title, String message,
            String priority, Map<String, String> data, List<String> channels) {
        return Mono.fromCallable((Callable<com.notificationservice.grpc.BulkSendResponse>) () -> {
            try {
                com.notificationservice.grpc.SendBulkNotificationsRequest.Builder builder = com.notificationservice.grpc.SendBulkNotificationsRequest
                        .newBuilder()
                        .addAllUserIds(userIds)
                        .setNotificationType(notificationType)
                        .setTitle(title)
                        .setMessage(message)
                        .setPriority(priority != null ? priority : "normal");
                if (data != null) {
                    builder.putAllData(data);
                }
                if (channels != null) {
                    builder.addAllChannels(channels);
                }
                return notificationServiceStub.sendBulkNotifications(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error sending bulk notifications: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to send bulk notifications");
            } catch (Exception e) {
                log.error("Unexpected error sending bulk notifications: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<com.notificationservice.grpc.NotificationListResponse> getNotifications(String userId,
            Boolean unreadOnly, String notificationType, int limit, int offset) {
        return Mono.fromCallable((Callable<com.notificationservice.grpc.NotificationListResponse>) () -> {
            try {
                com.notificationservice.grpc.GetNotificationsRequest.Builder builder = com.notificationservice.grpc.GetNotificationsRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setLimit(limit > 0 ? limit : 20)
                        .setOffset(offset);
                if (unreadOnly != null) {
                    builder.setUnreadOnly(unreadOnly);
                }
                if (notificationType != null) {
                    builder.setNotificationType(notificationType);
                }
                return notificationServiceStub.getNotifications(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting notifications: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get notifications");
            } catch (Exception e) {
                log.error("Unexpected error getting notifications: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }

    public Mono<com.notificationservice.grpc.UnreadCountResponse> getUnreadCount(String userId) {
        return Mono.fromCallable((Callable<com.notificationservice.grpc.UnreadCountResponse>) () -> {
            try {
                com.notificationservice.grpc.GetUnreadCountRequest request = com.notificationservice.grpc.GetUnreadCountRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                return notificationServiceStub.getUnreadCount(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting unread count: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get unread count");
            } catch (Exception e) {
                log.error("Unexpected error getting unread count: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<Void> markAsRead(String notificationId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.MarkAsReadRequest request = com.notificationservice.grpc.MarkAsReadRequest
                        .newBuilder()
                        .setNotificationId(notificationId)
                        .setUserId(userId)
                        .build();
                notificationServiceStub.markAsRead(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error marking as read: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to mark notification as read");
            } catch (Exception e) {
                log.error("Unexpected error marking as read: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<Void> markAllAsRead(String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.MarkAllAsReadRequest request = com.notificationservice.grpc.MarkAllAsReadRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                notificationServiceStub.markAllAsRead(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error marking all as read: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to mark all notifications as read");
            } catch (Exception e) {
                log.error("Unexpected error marking all as read: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<Void> deleteNotification(String notificationId, String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.DeleteNotificationRequest request = com.notificationservice.grpc.DeleteNotificationRequest
                        .newBuilder()
                        .setNotificationId(notificationId)
                        .setUserId(userId)
                        .build();
                notificationServiceStub.deleteNotification(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error deleting notification: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to delete notification");
            } catch (Exception e) {
                log.error("Unexpected error deleting notification: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<Void> deleteAllNotifications(String userId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.DeleteAllNotificationsRequest request = com.notificationservice.grpc.DeleteAllNotificationsRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                notificationServiceStub.deleteAllNotifications(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error deleting all notifications: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to delete all notifications");
            } catch (Exception e) {
                log.error("Unexpected error deleting all notifications: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public Mono<Void> registerPushToken(String userId, String deviceId, String token, String platform) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.RegisterPushTokenRequest request = com.notificationservice.grpc.RegisterPushTokenRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setDeviceId(deviceId)
                        .setToken(token)
                        .setPlatform(platform)
                        .build();
                notificationServiceStub.registerPushToken(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error registering push token: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to register push token");
            } catch (Exception e) {
                log.error("Unexpected error registering push token: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }

    public Mono<Void> unregisterPushToken(String userId, String deviceId) {
        return Mono.fromCallable((Callable<Void>) () -> {
            try {
                com.notificationservice.grpc.UnregisterPushTokenRequest request = com.notificationservice.grpc.UnregisterPushTokenRequest
                        .newBuilder()
                        .setUserId(userId)
                        .setDeviceId(deviceId)
                        .build();
                notificationServiceStub.unregisterPushToken(request);
                return null;
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error unregistering push token: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to unregister push token");
            } catch (Exception e) {
                log.error("Unexpected error unregistering push token: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }

    public Mono<com.notificationservice.grpc.PreferencesResponse> getPreferences(String userId) {
        return Mono.fromCallable((Callable<com.notificationservice.grpc.PreferencesResponse>) () -> {
            try {
                com.notificationservice.grpc.GetPreferencesRequest request = com.notificationservice.grpc.GetPreferencesRequest
                        .newBuilder()
                        .setUserId(userId)
                        .build();
                return notificationServiceStub.getPreferences(request);
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error getting preferences: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to get notification preferences");
            } catch (Exception e) {
                log.error("Unexpected error getting preferences: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }

    public Mono<com.notificationservice.grpc.PreferencesResponse> updatePreferences(String userId,
            Boolean emailEnabled, Boolean pushEnabled, Boolean websocketEnabled,
            Boolean fileNotifications, Boolean syncNotifications, Boolean shareNotifications,
            Boolean adminNotifications, Boolean systemNotifications, Boolean quietHoursEnabled,
            String quietHoursStart, String quietHoursEnd) {
        return Mono.fromCallable((Callable<com.notificationservice.grpc.PreferencesResponse>) () -> {
            try {
                com.notificationservice.grpc.UpdatePreferencesRequest.Builder builder = com.notificationservice.grpc.UpdatePreferencesRequest
                        .newBuilder()
                        .setUserId(userId);
                if (emailEnabled != null)
                    builder.setEmailEnabled(emailEnabled);
                if (pushEnabled != null)
                    builder.setPushEnabled(pushEnabled);
                if (websocketEnabled != null)
                    builder.setWebsocketEnabled(websocketEnabled);
                if (fileNotifications != null)
                    builder.setFileNotifications(fileNotifications);
                if (syncNotifications != null)
                    builder.setSyncNotifications(syncNotifications);
                if (shareNotifications != null)
                    builder.setShareNotifications(shareNotifications);
                if (adminNotifications != null)
                    builder.setAdminNotifications(adminNotifications);
                if (systemNotifications != null)
                    builder.setSystemNotifications(systemNotifications);
                if (quietHoursEnabled != null)
                    builder.setQuietHoursEnabled(quietHoursEnabled);
                if (quietHoursStart != null)
                    builder.setQuietHoursStart(quietHoursStart);
                if (quietHoursEnd != null)
                    builder.setQuietHoursEnd(quietHoursEnd);
                return notificationServiceStub.updatePreferences(builder.build());
            } catch (io.grpc.StatusRuntimeException e) {
                log.error("gRPC error updating preferences: {} - {}", e.getStatus().getCode(),
                        e.getStatus().getDescription());
                throw new RuntimeException(e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : "Failed to update notification preferences");
            } catch (Exception e) {
                log.error("Unexpected error updating preferences: {}", e.getMessage(), e);
                throw new RuntimeException("Notification service error");
            }
        });
    }
}
