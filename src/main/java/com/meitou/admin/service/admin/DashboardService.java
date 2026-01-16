package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理端数据概览服务类
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;
    
    /**
     * 获取统计数据
     */
    public Map<String, Object> getStats(Long siteId, String timeRange, String startDateStr, String endDateStr) {
        // If current user is not super admin (siteId != 0), force filter by their siteId
        Long contextSiteId = SiteContext.getSiteId();
        if (contextSiteId != null && contextSiteId != 0L) {
            siteId = contextSiteId;
        }

        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime start = getStartDateTime(timeRange, startDateStr, endDateStr);
        LocalDateTime end = getEndDateTime(timeRange, startDateStr, endDateStr);
        
        // 1. 平台积分总余额 (Total Balance of all users)
        // If siteId is provided, sum balance of users in that site.
        QueryWrapper<User> balanceWrapper = new QueryWrapper<>();
        balanceWrapper.select("sum(balance) as total");
        if (siteId != null) {
            balanceWrapper.eq("site_id", siteId);
        }
        List<Map<String, Object>> balanceList = userMapper.selectMapsIgnoreTenant(balanceWrapper);
        Map<String, Object> balanceResult = (balanceList != null && !balanceList.isEmpty()) ? balanceList.get(0) : null;
        long totalBalance = 0;
        if (balanceResult != null && balanceResult.get("total") != null) {
            totalBalance = Long.parseLong(balanceResult.get("total").toString());
        }
        stats.put("totalBalance", totalBalance);
        
        // 2. 商家总数 (Total Users)
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        if (siteId != null) {
            userWrapper.eq("site_id", siteId);
        }
        long totalUsers = userMapper.selectCountIgnoreTenant(userWrapper);
        stats.put("totalUsers", totalUsers);
        
        // 3. 产生消耗商家数 (Active Users)
        // Count distinct users who consumed in the time range
        QueryWrapper<UserTransaction> activeUserWrapper = new QueryWrapper<>();
        activeUserWrapper.select("count(distinct user_id) as count")
                .eq("type", "CONSUME")
                .ge(start != null, "created_at", start)
                .le(end != null, "created_at", end);
        if (siteId != null) {
            activeUserWrapper.eq("site_id", siteId);
        }
        List<Map<String, Object>> activeUserList = userTransactionMapper.selectMapsIgnoreTenant(activeUserWrapper);
        Map<String, Object> activeUserResult = (activeUserList != null && !activeUserList.isEmpty()) ? activeUserList.get(0) : null;
        long activeUsers = 0;
        if (activeUserResult != null && activeUserResult.get("count") != null) {
            activeUsers = Long.parseLong(activeUserResult.get("count").toString());
        }
        stats.put("activeUsers", activeUsers);
        
        // 4. 该类目总消耗 (Total Consumption)
        QueryWrapper<UserTransaction> consumptionWrapper = new QueryWrapper<>();
        consumptionWrapper.select("sum(abs(amount)) as total")
                .eq("type", "CONSUME")
                .ge(start != null, "created_at", start)
                .le(end != null, "created_at", end);
        if (siteId != null) {
            consumptionWrapper.eq("site_id", siteId);
        }
        List<Map<String, Object>> consumptionList = userTransactionMapper.selectMapsIgnoreTenant(consumptionWrapper);
        Map<String, Object> consumptionResult = (consumptionList != null && !consumptionList.isEmpty()) ? consumptionList.get(0) : null;
        long totalConsumption = 0;
        if (consumptionResult != null && consumptionResult.get("total") != null) {
            totalConsumption = Long.parseLong(consumptionResult.get("total").toString()); // Assuming amount is Integer but sum can be Long
        }
        stats.put("totalConsumption", totalConsumption);
        
        return stats;
    }
    
    /**
     * 获取趋势数据
     */
    public Map<String, Object> getTrend(Long siteId, String timeRange, String startDateStr, String endDateStr) {
        // If current user is not super admin (siteId != 0), force filter by their siteId
        Long contextSiteId = SiteContext.getSiteId();
        if (contextSiteId != null && contextSiteId != 0L) {
            siteId = contextSiteId;
        }

        Map<String, Object> result = new HashMap<>();
        
        LocalDateTime start = getStartDateTime(timeRange, startDateStr, endDateStr);
        LocalDateTime end = getEndDateTime(timeRange, startDateStr, endDateStr);
        
        if (start == null) start = LocalDateTime.now().minusDays(6).with(LocalTime.MIN);
        if (end == null) end = LocalDateTime.now().with(LocalTime.MAX);
        
        // Generate date list
        List<String> dateList = new ArrayList<>();
        LocalDate currentDate = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        DateTimeFormatter sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        while (!currentDate.isAfter(endDate)) {
            dateList.add(currentDate.format(formatter));
            currentDate = currentDate.plusDays(1);
        }
        
        // Query New Merchants (Users) Group by Date
        QueryWrapper<User> userWrapper = new QueryWrapper<>();
        userWrapper.select("DATE_FORMAT(created_at, '%m-%d') as date, count(*) as count")
                .ge("created_at", start)
                .le("created_at", end)
                .groupBy("date");
        if (siteId != null) {
            userWrapper.eq("site_id", siteId);
        }
        List<Map<String, Object>> userTrend = userMapper.selectMapsIgnoreTenant(userWrapper);
        Map<String, Long> userTrendMap = userTrend.stream()
                .filter(m -> m != null && m.get("date") != null)
                .collect(Collectors.toMap(
                m -> (String) m.get("date"),
                m -> {
                    Object count = m.get("count");
                    return count != null ? Long.parseLong(count.toString()) : 0L;
                },
                (v1, v2) -> v1 // Merge function in case of duplicate keys (shouldn't happen with group by)
        ));
        
        // Query Consumption Group by Date
        QueryWrapper<UserTransaction> consumeWrapper = new QueryWrapper<>();
        consumeWrapper.select("DATE_FORMAT(created_at, '%m-%d') as date, sum(abs(amount)) as total")
                .eq("type", "CONSUME")
                .ge("created_at", start)
                .le("created_at", end)
                .groupBy("date");
        if (siteId != null) {
            consumeWrapper.eq("site_id", siteId);
        }
        List<Map<String, Object>> consumeTrend = userTransactionMapper.selectMapsIgnoreTenant(consumeWrapper);
        Map<String, Long> consumeTrendMap = consumeTrend.stream()
                .filter(m -> m != null && m.get("date") != null)
                .collect(Collectors.toMap(
                m -> (String) m.get("date"),
                m -> {
                    Object total = m.get("total");
                    return total != null ? Long.parseLong(total.toString()) : 0L;
                },
                (v1, v2) -> v1
        ));
        
        // Combine
        List<Map<String, Object>> trendData = new ArrayList<>();
        currentDate = start.toLocalDate();
        while (!currentDate.isAfter(endDate)) {
            String dateKey = currentDate.format(formatter);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", dateKey);
            dayData.put("merchants", userTrendMap.getOrDefault(dateKey, 0L));
            dayData.put("consumption", consumeTrendMap.getOrDefault(dateKey, 0L));
            trendData.add(dayData);
            currentDate = currentDate.plusDays(1);
        }
        
        result.put("trendData", trendData);
        return result;
    }
    
    /**
     * 获取排名数据
     */
    public Map<String, Object> getRanking(Long siteId, String timeRange, String startDateStr, String endDateStr) {
        // If current user is not super admin (siteId != 0), force filter by their siteId
        Long contextSiteId = SiteContext.getSiteId();
        if (contextSiteId != null && contextSiteId != 0L) {
            siteId = contextSiteId;
        }

        Map<String, Object> result = new HashMap<>();
        
        LocalDateTime start = getStartDateTime(timeRange, startDateStr, endDateStr);
        LocalDateTime end = getEndDateTime(timeRange, startDateStr, endDateStr);
        
        // Top 5 users by consumption
        QueryWrapper<UserTransaction> rankingWrapper = new QueryWrapper<>();
        rankingWrapper.select("user_id, sum(abs(amount)) as total")
                .eq("type", "CONSUME")
                .ge(start != null, "created_at", start)
                .le(end != null, "created_at", end);
        if (siteId != null) {
            rankingWrapper.eq("site_id", siteId);
        }
        rankingWrapper.groupBy("user_id")
                .orderByDesc("total")
                .last("LIMIT 5");
        
        List<Map<String, Object>> rankingList = userTransactionMapper.selectMapsIgnoreTenant(rankingWrapper);
        
        List<Map<String, Object>> finalRanking = new ArrayList<>();
        if (!rankingList.isEmpty()) {
            List<Long> userIds = rankingList.stream()
                    .map(m -> Long.parseLong(m.get("user_id").toString()))
                    .collect(Collectors.toList());
            
            List<User> users = userMapper.selectListIgnoreTenant(new QueryWrapper<User>().in("id", userIds));
            Map<Long, String> userNameMap = users.stream().collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : "User " + u.getId()));
            
            for (Map<String, Object> item : rankingList) {
                Long userId = Long.parseLong(item.get("user_id").toString());
                Map<String, Object> rankItem = new HashMap<>();
                rankItem.put("name", userNameMap.getOrDefault(userId, "Unknown"));
                rankItem.put("value", Long.parseLong(item.get("total").toString()));
                finalRanking.add(rankItem);
            }
        }
        
        result.put("rankingData", finalRanking);
        return result;
    }
    
    private LocalDateTime getStartDateTime(String timeRange, String startDateStr, String endDateStr) {
        if ("custom".equals(timeRange) && startDateStr != null && !startDateStr.isEmpty()) {
            return LocalDate.parse(startDateStr).atStartOfDay();
        }
        
        LocalDate today = LocalDate.now();
        if ("today".equals(timeRange)) {
            return today.atStartOfDay();
        } else if ("week".equals(timeRange)) {
            // "This week" or "Last 7 days". Dashboard typically shows trend.
            // If "week", let's assume last 7 days including today.
            return today.minusDays(6).atStartOfDay();
        } else if ("month".equals(timeRange)) {
            return today.withDayOfMonth(1).atStartOfDay();
        }
        
        // Default to week
        return today.minusDays(6).atStartOfDay();
    }
    
    private LocalDateTime getEndDateTime(String timeRange, String startDateStr, String endDateStr) {
        if ("custom".equals(timeRange) && endDateStr != null && !endDateStr.isEmpty()) {
            return LocalDate.parse(endDateStr).atTime(LocalTime.MAX);
        }
        return LocalDateTime.now().with(LocalTime.MAX);
    }
}
