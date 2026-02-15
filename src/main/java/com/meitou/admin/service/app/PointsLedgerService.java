package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.RechargeOrder;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserPointBucket;
import com.meitou.admin.entity.UserPointBucketUsage;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserPointBucketMapper;
import com.meitou.admin.mapper.UserPointBucketUsageMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointsLedgerService {

    private final UserPointBucketMapper userPointBucketMapper;
    private final UserPointBucketUsageMapper userPointBucketUsageMapper;
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;

    @Transactional
    public void applyRechargePaid(RechargeOrder order) {
        if (order == null || order.getUserId() == null || order.getPoints() == null || order.getPoints() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "充值订单参数无效");
        }
        ensureLegacyBucket(order.getUserId());

        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime grantedAt = order.getPaidAt() != null ? order.getPaidAt() : now;
        LocalDateTime expiresAt = grantedAt.plusMonths(6);

        UserPointBucket bucket = new UserPointBucket();
        bucket.setUserId(order.getUserId());
        bucket.setSourceType("RECHARGE");
        bucket.setSourceRefId(order.getOrderNo());
        bucket.setTotalPoints(order.getPoints());
        bucket.setRemainingPoints(order.getPoints());
        bucket.setGrantedAt(grantedAt);
        bucket.setExpiresAt(expiresAt);
        bucket.setStatus("active");
        bucket.setSiteId(siteId);
        bucket.setCreatedAt(now);
        bucket.setUpdatedAt(now);
        bucket.setDeleted(0);
        userPointBucketMapper.insert(bucket);

        int updatedRows = userMapper.incrementBalance(order.getUserId(), order.getPoints(), now);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "用户余额更新失败");
        }

        User userAfter = userMapper.selectById(order.getUserId());
        if (userAfter == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(order.getUserId());
        transaction.setType("RECHARGE");
        transaction.setAmount(order.getPoints());
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceId(order.getId());
        transaction.setDescription("算力充值");
        transaction.setSiteId(siteId);
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        int inserted = userTransactionMapper.insert(transaction);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
        }
    }

    @Transactional
    public int deduct(Long userId, int cost, String businessType, Long businessId, String description) {
        if (userId == null || cost < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣减参数无效");
        }
        if (cost == 0) {
            User user = userMapper.selectById(userId);
            return user != null && user.getBalance() != null ? user.getBalance() : 0;
        }

        ensureLegacyBucket(userId);

        int available = userPointBucketMapper.sumAvailablePoints(userId);
        if (available < cost) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = userMapper.deductBalance(userId, cost, now);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        List<UserPointBucket> buckets = userPointBucketMapper.selectActiveBucketsForUpdate(userId);
        int remaining = cost;
        for (UserPointBucket bucket : buckets) {
            if (remaining <= 0) {
                break;
            }
            Integer bucketRemaining = bucket.getRemainingPoints();
            if (bucketRemaining == null || bucketRemaining <= 0) {
                continue;
            }
            int use = Math.min(bucketRemaining, remaining);
            bucket.setRemainingPoints(bucketRemaining - use);
            bucket.setUpdatedAt(now);
            userPointBucketMapper.updateById(bucket);

            UserPointBucketUsage usage = new UserPointBucketUsage();
            usage.setUserId(userId);
            usage.setBucketId(bucket.getId());
            usage.setBusinessType(businessType);
            usage.setBusinessId(businessId);
            usage.setPoints(use);
            usage.setCreatedAt(now);
            usage.setDeleted(0);
            usage.setSiteId(SiteContext.getSiteId());
            userPointBucketUsageMapper.insert(usage);

            remaining -= use;
        }

        if (remaining != 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "积分桶扣减失败");
        }

        User userAfter = userMapper.selectById(userId);
        if (userAfter == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(userId);
        transaction.setType("CONSUME");
        transaction.setAmount(-cost);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceId(businessId);
        transaction.setDescription(description);
        transaction.setSiteId(SiteContext.getSiteId());
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        int inserted = userTransactionMapper.insert(transaction);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
        }

        return balanceAfter;
    }

    @Transactional
    public int refund(Long userId, String businessType, Long businessId, String description) {
        if (userId == null || businessId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "退款参数无效");
        }

        LambdaQueryWrapper<UserTransaction> alreadyRefunded = new LambdaQueryWrapper<>();
        alreadyRefunded.eq(UserTransaction::getUserId, userId);
        alreadyRefunded.eq(UserTransaction::getType, "REFUND");
        alreadyRefunded.eq(UserTransaction::getReferenceId, businessId);
        alreadyRefunded.eq(UserTransaction::getDeleted, 0);
        if (userTransactionMapper.selectCount(alreadyRefunded) > 0) {
            return 0;
        }

        ensureLegacyBucket(userId);

        List<UserPointBucketUsage> usages = userPointBucketUsageMapper.selectByBusiness(userId, businessType, businessId);
        int total = usages.stream().mapToInt(u -> u.getPoints() != null ? u.getPoints() : 0).sum();
        if (total <= 0) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int toNewBucket = 0;
        for (UserPointBucketUsage usage : usages) {
            if (usage.getPoints() == null || usage.getPoints() <= 0 || usage.getBucketId() == null) {
                continue;
            }
            UserPointBucket bucket = userPointBucketMapper.selectById(usage.getBucketId());
            if (bucket == null) {
                toNewBucket += usage.getPoints();
                continue;
            }
            boolean bucketExpired = (bucket.getExpiresAt() != null && !bucket.getExpiresAt().isAfter(now)) || !"active".equals(bucket.getStatus());
            if (bucketExpired) {
                toNewBucket += usage.getPoints();
                continue;
            }
            int currentRemaining = bucket.getRemainingPoints() != null ? bucket.getRemainingPoints() : 0;
            int cap = bucket.getTotalPoints() != null ? bucket.getTotalPoints() : Integer.MAX_VALUE;
            int nextRemaining = Math.min(cap, currentRemaining + usage.getPoints());
            bucket.setRemainingPoints(nextRemaining);
            bucket.setUpdatedAt(now);
            userPointBucketMapper.updateById(bucket);
        }

        if (toNewBucket > 0) {
            UserPointBucket refundBucket = new UserPointBucket();
            refundBucket.setUserId(userId);
            refundBucket.setSourceType("REFUND");
            refundBucket.setSourceRefId(String.valueOf(businessId));
            refundBucket.setTotalPoints(toNewBucket);
            refundBucket.setRemainingPoints(toNewBucket);
            refundBucket.setGrantedAt(now);
            refundBucket.setExpiresAt(null);
            refundBucket.setStatus("active");
            refundBucket.setSiteId(SiteContext.getSiteId());
            refundBucket.setCreatedAt(now);
            refundBucket.setUpdatedAt(now);
            refundBucket.setDeleted(0);
            userPointBucketMapper.insert(refundBucket);
        }

        int updatedRows = userMapper.incrementBalance(userId, total, now);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "用户余额更新失败");
        }

        User userAfter = userMapper.selectById(userId);
        if (userAfter == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(userId);
        transaction.setType("REFUND");
        transaction.setAmount(total);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceId(businessId);
        transaction.setDescription(description);
        transaction.setSiteId(SiteContext.getSiteId());
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        int inserted = userTransactionMapper.insert(transaction);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
        }

        return total;
    }

    @Transactional
    public void grantNonExpiringPoints(Long userId, int points, String sourceType, String sourceRefId, String description, Long referenceId) {
        if (userId == null || points <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "积分发放参数无效");
        }
        ensureLegacyBucket(userId);

        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
        }

        LocalDateTime now = LocalDateTime.now();

        UserPointBucket bucket = new UserPointBucket();
        bucket.setUserId(userId);
        bucket.setSourceType(sourceType);
        bucket.setSourceRefId(sourceRefId);
        bucket.setTotalPoints(points);
        bucket.setRemainingPoints(points);
        bucket.setGrantedAt(now);
        bucket.setExpiresAt(null);
        bucket.setStatus("active");
        bucket.setSiteId(siteId);
        bucket.setCreatedAt(now);
        bucket.setUpdatedAt(now);
        bucket.setDeleted(0);
        userPointBucketMapper.insert(bucket);

        int updatedRows = userMapper.incrementBalance(userId, points, now);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "用户余额更新失败");
        }

        User userAfter = userMapper.selectById(userId);
        if (userAfter == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(userId);
        transaction.setType(sourceType);
        transaction.setAmount(points);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceId(referenceId);
        transaction.setDescription(description);
        transaction.setSiteId(siteId);
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        userTransactionMapper.insert(transaction);
    }

    @Transactional
    public void grantExpiringPoints(Long userId, int points, String sourceType, String sourceRefId, LocalDateTime expiresAt,
                                    String description, Long referenceId) {
        if (userId == null || points <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "积分发放参数无效");
        }
        if (expiresAt == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "过期时间不能为空");
        }
        ensureLegacyBucket(userId);

        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
        }

        LocalDateTime now = LocalDateTime.now();

        UserPointBucket bucket = new UserPointBucket();
        bucket.setUserId(userId);
        bucket.setSourceType(sourceType);
        bucket.setSourceRefId(sourceRefId);
        bucket.setTotalPoints(points);
        bucket.setRemainingPoints(points);
        bucket.setGrantedAt(now);
        bucket.setExpiresAt(expiresAt);
        bucket.setStatus("active");
        bucket.setSiteId(siteId);
        bucket.setCreatedAt(now);
        bucket.setUpdatedAt(now);
        bucket.setDeleted(0);
        userPointBucketMapper.insert(bucket);

        int updatedRows = userMapper.incrementBalance(userId, points, now);
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "用户余额更新失败");
        }

        User userAfter = userMapper.selectById(userId);
        if (userAfter == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(userId);
        transaction.setType(sourceType);
        transaction.setAmount(points);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setReferenceId(referenceId);
        transaction.setDescription(description);
        transaction.setSiteId(siteId);
        transaction.setCreatedAt(now);
        transaction.setDeleted(0);
        userTransactionMapper.insert(transaction);
    }

    private void ensureLegacyBucket(Long userId) {
        if (userPointBucketMapper.countByUserId(userId) > 0) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Integer balance = user.getBalance();
        if (balance == null || balance <= 0) {
            return;
        }

        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
        }

        LocalDateTime now = LocalDateTime.now();

        UserPointBucket bucket = new UserPointBucket();
        bucket.setUserId(userId);
        bucket.setSourceType("LEGACY");
        bucket.setSourceRefId(null);
        bucket.setTotalPoints(balance);
        bucket.setRemainingPoints(balance);
        bucket.setGrantedAt(user.getCreatedAt() != null ? user.getCreatedAt() : now);
        bucket.setExpiresAt(null);
        bucket.setStatus("active");
        bucket.setSiteId(siteId);
        bucket.setCreatedAt(now);
        bucket.setUpdatedAt(now);
        bucket.setDeleted(0);
        userPointBucketMapper.insert(bucket);
    }
}
