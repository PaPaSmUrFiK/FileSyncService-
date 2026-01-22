package com.notificationservice.grpc;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.model.domain.NotificationPreference;
import com.notificationservice.service.NotificationService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class NotificationGrpcServiceImpl extends NotificationServiceGrpc.NotificationServiceImplBase {

    private final NotificationService notificationService;

    @Override
    public void sendNotification(SendNotificationRequest request, StreamObserver<EmptyResponse> responseObserver) {
        notificationService.sendNotification(
                UUID.fromString(request.getUserId()),
                request.getNotificationType(),
                request.getTitle(),
                request.getMessage(),
                request.getPriority(),
                null, // resourceId - not available in gRPC request yet
                null, // resourceType
                request.getDataMap(),
                request.getChannelsList()).subscribe(
                        v -> {
                            responseObserver.onNext(EmptyResponse.newBuilder().build());
                            responseObserver.onCompleted();
                        },
                        e -> responseObserver.onError(e));
    }

    @Override
    public void getNotifications(GetNotificationsRequest request,
            StreamObserver<NotificationListResponse> responseObserver) {
        notificationService.getNotifications(
                UUID.fromString(request.getUserId()),
                request.hasUnreadOnly() && request.getUnreadOnly(),
                request.hasNotificationType() ? request.getNotificationType() : null,
                request.getLimit() > 0 ? request.getLimit() : 20,
                request.getOffset()).collectList()
                .zipWith(notificationService.getUnreadCount(UUID.fromString(request.getUserId())))
                .subscribe(
                        tuple -> {
                            NotificationListResponse response = NotificationListResponse.newBuilder()
                                    .addAllNotifications(
                                            tuple.getT1().stream().map(this::mapToInfo).collect(Collectors.toList()))
                                    .setTotal(tuple.getT1().size())
                                    .setUnreadCount(tuple.getT2().intValue())
                                    .build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        e -> {
                            log.error("Error getting notifications for user {}: {}", request.getUserId(),
                                    e.getMessage(), e);
                            responseObserver.onError(e);
                        });
    }

    @Override
    public void getUnreadCount(GetUnreadCountRequest request, StreamObserver<UnreadCountResponse> responseObserver) {
        notificationService.getUnreadCount(UUID.fromString(request.getUserId()))
                .subscribe(
                        count -> {
                            responseObserver
                                    .onNext(UnreadCountResponse.newBuilder().setCount(count.intValue()).build());
                            responseObserver.onCompleted();
                        },
                        e -> {
                            log.error("Error getting unread count for user {}: {}", request.getUserId(), e.getMessage(),
                                    e);
                            responseObserver.onError(e);
                        });
    }

    @Override
    public void markAsRead(MarkAsReadRequest request, StreamObserver<EmptyResponse> responseObserver) {
        notificationService
                .markAsRead(UUID.fromString(request.getNotificationId()), UUID.fromString(request.getUserId()))
                .doOnSuccess(v -> {
                    responseObserver.onNext(EmptyResponse.newBuilder().build());
                    responseObserver.onCompleted();
                })
                .doOnError(e -> {
                    log.error("Error marking notification as read: {}", e.getMessage(), e);
                    responseObserver.onError(e);
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
    }

    @Override
    public void markAllAsRead(MarkAllAsReadRequest request, StreamObserver<EmptyResponse> responseObserver) {
        notificationService.markAllAsRead(UUID.fromString(request.getUserId()))
                .doOnSuccess(v -> {
                    responseObserver.onNext(EmptyResponse.newBuilder().build());
                    responseObserver.onCompleted();
                })
                .doOnError(e -> {
                    log.error("Error marking all notifications as read: {}", e.getMessage(), e);
                    responseObserver.onError(e);
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
    }

    @Override
    public void deleteNotification(DeleteNotificationRequest request, StreamObserver<EmptyResponse> responseObserver) {
        notificationService
                .deleteNotification(UUID.fromString(request.getNotificationId()),
                        UUID.fromString(request.getUserId()))
                .doOnSuccess(v -> {
                    responseObserver.onNext(EmptyResponse.newBuilder().build());
                    responseObserver.onCompleted();
                })
                .doOnError(e -> {
                    log.error("Error deleting notification: {}", e.getMessage(), e);
                    responseObserver.onError(e);
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
    }

    @Override
    public void deleteAllNotifications(DeleteAllNotificationsRequest request,
            StreamObserver<EmptyResponse> responseObserver) {
        notificationService.deleteAllNotifications(UUID.fromString(request.getUserId()))
                .doOnSuccess(v -> {
                    responseObserver.onNext(EmptyResponse.newBuilder().build());
                    responseObserver.onCompleted();
                })
                .doOnError(e -> {
                    log.error("Error deleting all notifications: {}", e.getMessage(), e);
                    responseObserver.onError(e);
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
    }

    @Override
    public void getPreferences(GetPreferencesRequest request, StreamObserver<PreferencesResponse> responseObserver) {
        notificationService.getPreferences(UUID.fromString(request.getUserId()))
                .subscribe(
                        prefs -> {
                            responseObserver.onNext(mapToPreferencesResponse(prefs));
                            responseObserver.onCompleted();
                        },
                        e -> responseObserver.onError(e));
    }

    @Override
    public void updatePreferences(UpdatePreferencesRequest request,
            StreamObserver<PreferencesResponse> responseObserver) {
        NotificationPreference.NotificationPreferenceBuilder builder = NotificationPreference.builder()
                .userId(UUID.fromString(request.getUserId()));

        if (request.hasEmailEnabled())
            builder.emailEnabled(request.getEmailEnabled());
        if (request.hasPushEnabled())
            builder.pushEnabled(request.getPushEnabled());
        if (request.hasWebsocketEnabled())
            builder.websocketEnabled(request.getWebsocketEnabled());
        if (request.hasFileNotifications())
            builder.fileNotifications(request.getFileNotifications());
        if (request.hasSyncNotifications())
            builder.syncNotifications(request.getSyncNotifications());
        if (request.hasShareNotifications())
            builder.shareNotifications(request.getShareNotifications());
        if (request.hasAdminNotifications())
            builder.adminNotifications(request.getAdminNotifications());
        if (request.hasSystemNotifications())
            builder.systemNotifications(request.getSystemNotifications());
        if (request.hasQuietHoursEnabled())
            builder.quietHoursEnabled(request.getQuietHoursEnabled());
        if (request.hasQuietHoursStart())
            builder.quietHoursStart(LocalTime.parse(request.getQuietHoursStart()));
        if (request.hasQuietHoursEnd())
            builder.quietHoursEnd(LocalTime.parse(request.getQuietHoursEnd()));

        notificationService.updatePreferences(UUID.fromString(request.getUserId()), builder.build())
                .subscribe(
                        prefs -> {
                            responseObserver.onNext(mapToPreferencesResponse(prefs));
                            responseObserver.onCompleted();
                        },
                        e -> responseObserver.onError(e));
    }

    private NotificationInfo mapToInfo(Notification n) {
        NotificationInfo.Builder builder = NotificationInfo.newBuilder()
                .setId(n.getId().toString())
                .setNotificationType(n.getNotificationType())
                .setTitle(n.getTitle())
                .setMessage(n.getMessage())
                .setPriority(n.getPriority())
                .setIsRead(n.isRead())
                .setCreatedAt(n.getCreatedAt().toString());

        if (n.getReadAt() != null) {
            builder.setReadAt(n.getReadAt().toString());
        }

        return builder.build();
    }

    private PreferencesResponse mapToPreferencesResponse(NotificationPreference p) {
        PreferencesResponse.Builder builder = PreferencesResponse.newBuilder()
                .setUserId(p.getUserId().toString())
                .setEmailEnabled(p.isEmailEnabled())
                .setPushEnabled(p.isPushEnabled())
                .setWebsocketEnabled(p.isWebsocketEnabled())
                .setFileNotifications(p.isFileNotifications())
                .setSyncNotifications(p.isSyncNotifications())
                .setShareNotifications(p.isShareNotifications())
                .setAdminNotifications(p.isAdminNotifications())
                .setSystemNotifications(p.isSystemNotifications())
                .setQuietHoursEnabled(p.isQuietHoursEnabled());

        if (p.getQuietHoursStart() != null) {
            builder.setQuietHoursStart(p.getQuietHoursStart().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (p.getQuietHoursEnd() != null) {
            builder.setQuietHoursEnd(p.getQuietHoursEnd().format(DateTimeFormatter.ofPattern("HH:mm")));
        }

        return builder.build();
    }
}
