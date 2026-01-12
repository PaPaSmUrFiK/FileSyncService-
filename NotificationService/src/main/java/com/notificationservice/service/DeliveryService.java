package com.notificationservice.service;

import com.notificationservice.model.domain.Notification;
import com.notificationservice.model.domain.NotificationPreference;
import reactor.core.publisher.Mono;

import java.util.List;

public interface DeliveryService {
    Mono<Void> deliver(Notification notification, List<String> requestedChannels);

    // Check if delivery is allowed based on preferences and quiet hours
    boolean isDeliveryAllowed(Notification notification, NotificationPreference preference, String channel);
}
