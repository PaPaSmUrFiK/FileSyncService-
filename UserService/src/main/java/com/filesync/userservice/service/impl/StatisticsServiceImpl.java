package com.filesync.userservice.service.impl;

import com.filesync.userservice.repository.UserQuotaRepository;
import com.filesync.userservice.repository.UserRepository;
import com.filesync.userservice.service.StatisticsService;
import com.filesync.userservice.service.integration.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserRepository userRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final AuthServiceClient authServiceClient;

    @Override
    public Map<String, Object> getSystemStatistics(Long from, Long to) {
        LocalDateTime now = LocalDateTime.now();
        // from and to are currently not used in the database counts below, but keeping
        // for future extension if needed.
        // For now, removing the unused local variables to satisfy lint.

        Map<String, Object> stats = new HashMap<>();

        // 1. Total users
        stats.put("total_users", (int) userRepository.count());

        // 2. Active users (Call Auth Service)
        stats.put("active_users_today", authServiceClient.getActiveUsersCount(now.minusDays(1), now));
        stats.put("active_users_week", authServiceClient.getActiveUsersCount(now.minusWeeks(1), now));
        stats.put("active_users_month", authServiceClient.getActiveUsersCount(now.minusMonths(1), now));

        // 3. New users
        stats.put("new_users_today", (int) userRepository.countUsersRegisteredBetween(now.minusDays(1), now));
        stats.put("new_users_week", (int) userRepository.countUsersRegisteredBetween(now.minusWeeks(1), now));
        stats.put("new_users_month", (int) userRepository.countUsersRegisteredBetween(now.minusMonths(1), now));

        // 4. Storage
        Long usedRaw = userRepository.getTotalStorageUsed();
        Long allocatedRaw = userRepository.getTotalStorageAllocated();
        long used = usedRaw != null ? usedRaw : 0L;
        long allocated = allocatedRaw != null ? allocatedRaw : 0L;

        stats.put("total_storage_used", used);
        stats.put("total_storage_allocated", allocated);

        if (allocated > 0) {
            stats.put("storage_usage_percentage", ((double) used / allocated) * 100);
        } else {
            stats.put("storage_usage_percentage", 0.0);
        }

        return stats;
    }

    @Override
    public Map<String, Object> getStorageStatistics() {
        Map<String, Object> stats = new HashMap<>();

        Long usedRaw = userRepository.getTotalStorageUsed();
        Long allocatedRaw = userRepository.getTotalStorageAllocated();

        stats.put("totalBytesUsed", usedRaw != null ? usedRaw : 0L);
        stats.put("totalBytesAllocated", allocatedRaw != null ? allocatedRaw : 0L);
        stats.put("storageByPlan", userQuotaRepository.getStorageStatsByPlan());
        stats.put("topUsers",
                userRepository.findTopUsersByStorage(org.springframework.data.domain.PageRequest.of(0, 10)));

        return stats;
    }

    @Override
    public Map<String, Object> getUserStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepository.count();
        stats.put("totalUsers", (int) totalUsers);

        LocalDateTime now = LocalDateTime.now();
        stats.put("newUsersLast24h", (int) userRepository.countUsersRegisteredBetween(now.minusHours(24), now));
        stats.put("activeUsersLastHour", authServiceClient.getActiveUsersCount(now.minusHours(1), now));

        // Get blocked users count from AuthService (source of truth)
        stats.put("blockedUsers", authServiceClient.getBlockedUsersCount());

        // Count admins by checking roles from AuthService
        int adminCount = authServiceClient.getAdminCount();
        stats.put("adminCount", adminCount);

        LocalDateTime fromDate = LocalDateTime.now().minusMonths(6);
        stats.put("usersByDate", userRepository.getRegistrationDynamics(fromDate));

        return stats;
    }

    @Override
    public Map<String, Object> getActiveUsers(int minutes) {
        List<String> activeUserIds = authServiceClient.getUsersActiveInLastMinutes(minutes);

        // Fetch details for these users
        // This logic will be simpler in the Controller/gRPC layer which maps it to
        // response.
        // Here we return the raw list or fetch entities.

        Map<String, Object> result = new HashMap<>();
        result.put("count", activeUserIds.size());
        result.put("user_ids", activeUserIds);

        return result;
    }
}
