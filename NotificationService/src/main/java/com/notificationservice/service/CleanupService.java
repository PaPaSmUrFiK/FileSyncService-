package com.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CleanupService {

    private final R2dbcEntityTemplate entityTemplate;

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    public void cleanupOldNotifications() {
        log.info("Starting scheduled cleanup of old notifications...");

        entityTemplate.getDatabaseClient()
                .sql("SELECT cleanup_old_notifications()")
                .fetch()
                .rowsUpdated()
                .subscribe(
                        count -> log.info("Cleanup completed successfully"),
                        error -> log.error("Error during scheduled cleanup: {}", error.getMessage()));
    }
}
