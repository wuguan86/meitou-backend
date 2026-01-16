package com.meitou.admin.service.common;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录尝试次数服务
 * 用于处理账号锁定逻辑
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 15;

    // 记录失败尝试次数：Key -> 次数
    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    
    // 记录锁定结束时间：Key -> 结束时间
    private final Map<String, LocalDateTime> lockouts = new ConcurrentHashMap<>();

    /**
     * 检查是否被锁定
     * @param key 标识（手机号/账号）
     * @return true=已锁定
     */
    public boolean isLocked(String key) {
        if (lockouts.containsKey(key)) {
            LocalDateTime unlockTime = lockouts.get(key);
            if (LocalDateTime.now().isBefore(unlockTime)) {
                return true;
            } else {
                // 锁定已过期，清理
                lockouts.remove(key);
                attempts.remove(key); // 重置尝试次数
                return false;
            }
        }
        return false;
    }

    /**
     * 登录失败
     * @param key 标识
     */
    public void loginFailed(String key) {
        // 如果已经锁定，不做处理
        if (isLocked(key)) {
            return;
        }

        int count = attempts.getOrDefault(key, 0) + 1;
        attempts.put(key, count);

        if (count >= MAX_ATTEMPTS) {
            // 达到最大尝试次数，锁定
            lockouts.put(key, LocalDateTime.now().plusMinutes(LOCK_TIME_MINUTES));
        }
    }

    /**
     * 登录成功
     * @param key 标识
     */
    public void loginSucceeded(String key) {
        attempts.remove(key);
        lockouts.remove(key);
    }
    
    /**
     * 获取剩余尝试次数
     */
    public int getRemainingAttempts(String key) {
        if (isLocked(key)) return 0;
        return Math.max(0, MAX_ATTEMPTS - attempts.getOrDefault(key, 0));
    }
    
    /**
     * 获取剩余锁定时间（秒）
     */
    public long getRemainingLockSeconds(String key) {
        if (!lockouts.containsKey(key)) return 0;
        LocalDateTime unlockTime = lockouts.get(key);
        long seconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), unlockTime);
        return Math.max(0, seconds);
    }
}
