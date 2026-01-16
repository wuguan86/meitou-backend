package com.meitou.admin.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的基于内存的频率限制器
 * 用于防止用户恶意刷订单
 */
@Slf4j
@Component
public class RateLimiter {

    // 存储每个key的访问记录
    private final Map<String, RateLimitInfo> limitMap = new ConcurrentHashMap<>();

    // 定期清理过期数据的间隔（毫秒）
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1分钟
    private long lastCleanupTime = System.currentTimeMillis();

    /**
     * 检查是否允许访问
     * 
     * @param key           限流key（如用户ID）
     * @param maxAttempts   最大访问次数
     * @param windowSeconds 时间窗口（秒）
     * @return true-允许访问，false-超过限制
     */
    public boolean tryAcquire(String key, int maxAttempts, int windowSeconds) {
        // 定期清理过期数据
        cleanupIfNeeded();

        RateLimitInfo info = limitMap.computeIfAbsent(key, k -> new RateLimitInfo());

        synchronized (info) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusSeconds(windowSeconds);

            // 移除过期的访问记录
            info.accessTimes.removeIf(time -> time.isBefore(windowStart));

            // 检查是否超过限制
            if (info.accessTimes.size() >= maxAttempts) {
                log.warn("频率限制触发：key={}, 当前次数={}, 限制={}/{}秒",
                        key, info.accessTimes.size(), maxAttempts, windowSeconds);
                return false;
            }

            // 记录本次访问
            info.accessTimes.add(now);
            return true;
        }
    }

    /**
     * 定期清理过期数据，避免内存泄漏
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
                    cleanup();
                    lastCleanupTime = now;
                }
            }
        }
    }

    /**
     * 清理长时间未使用的key
     */
    private void cleanup() {
        LocalDateTime threshold = LocalDateTime.now().minus(5, ChronoUnit.MINUTES);
        int beforeSize = limitMap.size();

        limitMap.entrySet().removeIf(entry -> {
            RateLimitInfo info = entry.getValue();
            synchronized (info) {
                // 移除5分钟内没有访问记录的key
                info.accessTimes.removeIf(time -> time.isBefore(threshold));
                return info.accessTimes.isEmpty();
            }
        });

        int afterSize = limitMap.size();
        if (beforeSize != afterSize) {
            log.debug("RateLimiter清理完成：清理前={}, 清理后={}, 清除={}个",
                    beforeSize, afterSize, beforeSize - afterSize);
        }
    }

    /**
     * 频率限制信息
     */
    private static class RateLimitInfo {
        // 使用 java.util.concurrent.CopyOnWriteArrayList 保证线程安全
        final java.util.List<LocalDateTime> accessTimes = new java.util.concurrent.CopyOnWriteArrayList<>();
    }
}
