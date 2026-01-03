package com.filesync.userservice.service;

import java.util.Map;

public interface StatisticsService {
    Map<String, Object> getSystemStatistics(Long from, Long to);

    Map<String, Object> getStorageStatistics();

    Map<String, Object> getUserStatistics();

    Map<String, Object> getActiveUsers(int minutes);
}
