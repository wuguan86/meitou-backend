package com.meitou.admin.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.entity.UserMembershipPeriod;
import com.meitou.admin.mapper.MembershipPackageMapper;
import com.meitou.admin.mapper.UserMembershipPeriodMapper;
import com.meitou.admin.service.app.PointsLedgerService;
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
public class MembershipActivateTask {

    private final UserMembershipPeriodMapper userMembershipPeriodMapper;
    private final MembershipPackageMapper membershipPackageMapper;
    private final PointsLedgerService pointsLedgerService;
    private final TransactionTemplate transactionTemplate;

    @Value("${membership.activate.batchSize:200}")
    private int batchSize;

    @Scheduled(fixedRateString = "${membership.activate.fixedRateMs:600000}")
    public void activateMemberships() {
        LocalDateTime now = LocalDateTime.now();
        List<UserMembershipPeriod> due = userMembershipPeriodMapper.selectDueScheduledIgnoreTenant(now, batchSize);
        if (due.isEmpty()) {
            return;
        }
        for (UserMembershipPeriod period : due) {
            if (period.getSiteId() == null) {
                continue;
            }
            runWithSiteContext(period.getSiteId(), () -> transactionTemplate.execute(status -> {
                activateSingle(period.getId());
                return null;
            }));
        }
    }

    private void activateSingle(Long periodId) {
        if (periodId == null) {
            return;
        }
        UserMembershipPeriod period = userMembershipPeriodMapper.selectById(periodId);
        if (period == null || period.getUserId() == null || !"scheduled".equals(period.getStatus())) {
            return;
        }
        if (period.getStartAt() != null && period.getStartAt().isAfter(LocalDateTime.now())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<UserMembershipPeriod> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserMembershipPeriod::getId, periodId);
        wrapper.eq(UserMembershipPeriod::getStatus, "scheduled");
        wrapper.set(UserMembershipPeriod::getStatus, "active");
        wrapper.set(UserMembershipPeriod::getUpdatedAt, now);
        int updated = userMembershipPeriodMapper.update(null, wrapper);
        if (updated <= 0) {
            return;
        }

        MembershipPackage pkg = membershipPackageMapper.selectById(period.getPackageId());
        if (pkg == null) {
            return;
        }
        Integer reward = pkg.getPointsReward();
        if (reward == null || reward <= 0 || period.getEndAt() == null) {
            return;
        }
        pointsLedgerService.grantExpiringPoints(period.getUserId(), reward, "MEMBERSHIP", period.getOrderNo(), period.getEndAt(),
                "会员赠送算力-" + period.getLevelCode(), period.getId());
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

