package com.meitou.admin.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserPointBucket;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserPointBucketMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBucketExpireTask {

    private final UserPointBucketMapper userPointBucketMapper;
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${points.bucket.expire.batchSize:200}")
    private int batchSize;

    @Scheduled(fixedRateString = "${points.bucket.expire.fixedRateMs:600000}")
    public void expireBuckets() {
        LocalDateTime now = LocalDateTime.now();
        List<UserPointBucket> expiredBuckets = userPointBucketMapper.selectExpiredBucketsIgnoreTenant(now, batchSize);
        if (expiredBuckets.isEmpty()) {
            return;
        }

        for (UserPointBucket bucket : expiredBuckets) {
            if (bucket.getSiteId() == null) {
                continue;
            }
            runWithSiteContext(bucket.getSiteId(), () -> transactionTemplate.execute(status -> {
                expireSingle(bucket.getId());
                return null;
            }));
        }
    }

    private void expireSingle(Long bucketId) {
        if (bucketId == null) {
            return;
        }
        UserPointBucket bucket = userPointBucketMapper.selectById(bucketId);
        if (bucket == null || bucket.getUserId() == null) {
            return;
        }
        if (!"active".equals(bucket.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (bucket.getExpiresAt() == null || bucket.getExpiresAt().isAfter(now)) {
            return;
        }
        Integer remaining = bucket.getRemainingPoints();
        if (remaining == null || remaining <= 0) {
            LambdaUpdateWrapper<UserPointBucket> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UserPointBucket::getId, bucketId);
            wrapper.eq(UserPointBucket::getStatus, "active");
            wrapper.set(UserPointBucket::getRemainingPoints, 0);
            wrapper.set(UserPointBucket::getStatus, "expired");
            wrapper.set(UserPointBucket::getUpdatedAt, now);
            userPointBucketMapper.update(null, wrapper);
            return;
        }

        User userBefore = userMapper.selectById(bucket.getUserId());
        if (userBefore == null) {
            return;
        }
        int beforeBalance = userBefore.getBalance() != null ? userBefore.getBalance() : 0;

        LambdaUpdateWrapper<UserPointBucket> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserPointBucket::getId, bucketId);
        wrapper.eq(UserPointBucket::getStatus, "active");
        wrapper.eq(UserPointBucket::getRemainingPoints, remaining);
        wrapper.le(UserPointBucket::getExpiresAt, now);
        wrapper.set(UserPointBucket::getRemainingPoints, 0);
        wrapper.set(UserPointBucket::getStatus, "expired");
        wrapper.set(UserPointBucket::getUpdatedAt, now);
        int updated = userPointBucketMapper.update(null, wrapper);
        if (updated <= 0) {
            return;
        }

        userMapper.decrementBalanceFloorZero(bucket.getUserId(), remaining, now);
        User userAfter = userMapper.selectById(bucket.getUserId());
        int afterBalance = userAfter != null && userAfter.getBalance() != null ? userAfter.getBalance() : 0;
        int delta = Math.max(0, beforeBalance - afterBalance);
        if (delta <= 0) {
            return;
        }
        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(bucket.getUserId());
        transaction.setType("EXPIRE");
        transaction.setAmount(-delta);
        transaction.setBalanceAfter(afterBalance);
        transaction.setReferenceId(bucketId);
        transaction.setDescription("积分到期清零");
        transaction.setSiteId(SiteContext.getSiteId());
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        int inserted = userTransactionMapper.insert(transaction);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
        }
    }

    private void runWithSiteContext(Long siteId, Runnable runnable) {
        Long originalSiteId = SiteContext.getSiteId();
        try {
            SiteContext.setSiteId(siteId);
            runnable.run();
        } finally {
            if (originalSiteId == null) {
                SiteContext.clear();
            } else {
                SiteContext.setSiteId(originalSiteId);
            }
        }
    }
}
