package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.dto.app.MembershipOrderCreateRequest;
import com.meitou.admin.dto.app.MembershipStatusResponse;
import com.meitou.admin.dto.app.RechargeOrderResponse;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.entity.PaymentConfig;
import com.meitou.admin.entity.RechargeOrder;
import com.meitou.admin.entity.UserMembershipPeriod;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.MembershipPackageMapper;
import com.meitou.admin.mapper.PaymentConfigMapper;
import com.meitou.admin.mapper.RechargeOrderMapper;
import com.meitou.admin.mapper.UserMembershipPeriodMapper;
import com.meitou.admin.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipOrderService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final PaymentConfigMapper paymentConfigMapper;
    private final MembershipPackageMapper membershipPackageMapper;
    private final UserMembershipPeriodMapper userMembershipPeriodMapper;
    private final PaymentService paymentService;
    private final PointsLedgerService pointsLedgerService;
    private final RateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public RechargeOrderResponse createOrder(Long userId, MembershipOrderCreateRequest request, String userAgent) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String rateLimitKey = "membership_create_" + userId;
        if (!rateLimiter.tryAcquire(rateLimitKey, 3, 60)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "操作过于频繁，请1分钟后再试");
        }

        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.SITE_NOT_FOUND);
        }

        String billingCycle = normalizeBillingCycle(request.getBillingCycle());
        MembershipPackage pkg = membershipPackageMapper.selectById(request.getPackageId());
        if (pkg == null || Boolean.FALSE.equals(pkg.getIsActive())) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_PACKAGE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        UserMembershipPeriod active = findActiveMembership(userId, now);
        if (active != null && active.getEndAt() != null && active.getEndAt().isAfter(now)
                && active.getLevelCode() != null
                && !active.getLevelCode().equals(pkg.getLevelCode())) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_TYPE_SWITCH_NOT_ALLOWED);
        }

        boolean oldUser = isOldUser(userId);
        BigDecimal amount = resolveAmount(pkg, billingCycle, oldUser);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "套餐价格配置异常");
        }

        String orderNo = generateOrderNo(userId);

        MembershipPayload payload = new MembershipPayload();
        payload.packageId = request.getPackageId();
        payload.billingCycle = billingCycle;

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setPoints(0);
        order.setProductType("membership");
        try {
            order.setProductPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "产品信息序列化失败");
        }
        order.setPaymentType(request.getPaymentType());
        order.setStatus("pending");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setDeleted(0);
        order.setSiteId(siteId);

        rechargeOrderMapper.insert(order);

        PaymentConfig paymentConfig = getPaymentConfig(request.getPaymentType());
        if (paymentConfig == null || !Boolean.TRUE.equals(paymentConfig.getIsEnabled())) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_DISABLED);
        }

        Map<String, String> paymentParams;
        try {
            if ("wechat".equals(request.getPaymentType())) {
                paymentParams = paymentService.createWechatPayment(orderNo, amount.toString(), "会员订阅", paymentConfig.getConfigJson());
            } else if ("alipay".equals(request.getPaymentType())) {
                paymentParams = paymentService.createAlipayPayment(orderNo, amount.toString(), "会员订阅", paymentConfig.getConfigJson(), userAgent);
            } else {
                throw new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
            }
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw (BusinessException) e;
            }
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(), "创建支付订单失败：" + e.getMessage());
        }

        order.setStatus("paying");
        order.setThirdPartyOrderNo(paymentParams.get("orderId"));
        order.setUpdatedAt(LocalDateTime.now());
        rechargeOrderMapper.updateById(order);

        RechargeOrderResponse response = new RechargeOrderResponse();
        response.setOrderNo(orderNo);
        response.setAmount(amount);
        response.setPoints(0);
        response.setPaymentType(request.getPaymentType());
        response.setStatus(order.getStatus());
        response.setProductType(order.getProductType());
        try {
            response.setPaymentParams(objectMapper.writeValueAsString(paymentParams));
        } catch (Exception e) {
            log.error("序列化支付参数失败", e);
        }
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }

    public MembershipStatusResponse getStatus(Long userId) {
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.SITE_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        UserMembershipPeriod active = findActiveMembership(userId, now);

        MembershipStatusResponse response = new MembershipStatusResponse();
        response.setOldUser(isOldUser(userId));

        if (active != null) {
            response.setActivePackageId(active.getPackageId());
            response.setActiveLevelCode(active.getLevelCode());
            response.setActiveBillingCycle(active.getBillingCycle());
            response.setActiveEndAt(active.getEndAt());
            response.setCanSwitchType(active.getEndAt() == null || !active.getEndAt().isAfter(now));
        } else {
            response.setCanSwitchType(true);
        }
        return response;
    }

    @Transactional
    public void applyPaidOrder(RechargeOrder order) {
        if (order == null || order.getUserId() == null || order.getOrderNo() == null) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_ORDER_INVALID);
        }
        if (order.getProductPayload() == null || order.getProductPayload().isBlank()) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_ORDER_INVALID);
        }

        MembershipPayload payload;
        try {
            payload = objectMapper.readValue(order.getProductPayload(), MembershipPayload.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_ORDER_INVALID.getCode(), "会员订单信息解析失败");
        }
        if (payload.packageId == null || payload.billingCycle == null) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_ORDER_INVALID);
        }

        MembershipPackage pkg = membershipPackageMapper.selectById(payload.packageId);
        if (pkg == null || Boolean.FALSE.equals(pkg.getIsActive())) {
            throw new BusinessException(ErrorCode.MEMBERSHIP_PACKAGE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        UserMembershipPeriod active = findActiveMembership(order.getUserId(), now);

        LocalDateTime startAt;
        String status;
        if (active != null && active.getEndAt() != null && active.getEndAt().isAfter(now)) {
            if (active.getLevelCode() != null && !active.getLevelCode().equals(pkg.getLevelCode())) {
                throw new BusinessException(ErrorCode.MEMBERSHIP_TYPE_SWITCH_NOT_ALLOWED);
            }
            startAt = active.getEndAt();
            status = "scheduled";
        } else {
            startAt = now;
            status = "active";
        }

        LocalDateTime endAt = "YEARLY".equals(payload.billingCycle) ? startAt.plusYears(1) : startAt.plusMonths(1);

        UserMembershipPeriod period = new UserMembershipPeriod();
        period.setUserId(order.getUserId());
        period.setPackageId(payload.packageId);
        period.setLevelCode(pkg.getLevelCode());
        period.setBillingCycle(payload.billingCycle);
        period.setStartAt(startAt);
        period.setEndAt(endAt);
        period.setStatus(status);
        period.setOrderNo(order.getOrderNo());
        period.setSiteId(SiteContext.getSiteId());
        period.setCreatedAt(now);
        period.setUpdatedAt(now);
        period.setDeleted(0);
        userMembershipPeriodMapper.insert(period);

        Integer reward = pkg.getPointsReward();
        if ("active".equals(status) && reward != null && reward > 0) {
            pointsLedgerService.grantExpiringPoints(order.getUserId(), reward, "MEMBERSHIP", order.getOrderNo(), endAt,
                    "会员赠送算力-" + pkg.getLevelCode(), period.getId());
        }
    }

    private boolean isOldUser(Long userId) {
        LambdaQueryWrapper<UserMembershipPeriod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMembershipPeriod::getUserId, userId);
        wrapper.eq(UserMembershipPeriod::getDeleted, 0);
        return userMembershipPeriodMapper.selectCount(wrapper) > 0;
    }

    private UserMembershipPeriod findActiveMembership(Long userId, LocalDateTime now) {
        LambdaQueryWrapper<UserMembershipPeriod> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMembershipPeriod::getUserId, userId);
        wrapper.eq(UserMembershipPeriod::getStatus, "active");
        wrapper.eq(UserMembershipPeriod::getDeleted, 0);
        wrapper.gt(UserMembershipPeriod::getEndAt, now);
        wrapper.orderByDesc(UserMembershipPeriod::getEndAt);
        wrapper.last("LIMIT 1");
        return userMembershipPeriodMapper.selectOne(wrapper);
    }

    private PaymentConfig getPaymentConfig(String paymentType) {
        LambdaQueryWrapper<PaymentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentConfig::getPaymentType, paymentType);
        wrapper.eq(PaymentConfig::getDeleted, 0);
        return paymentConfigMapper.selectOne(wrapper);
    }

    private BigDecimal resolveAmount(MembershipPackage pkg, String billingCycle, boolean oldUser) {
        if ("YEARLY".equals(billingCycle)) {
            if (oldUser) {
                return pkg.getYearlyPrice();
            }
            if (pkg.getYearlyDiscountPrice() != null) {
                return pkg.getYearlyDiscountPrice();
            }
            return pkg.getYearlyPrice();
        }
        if (oldUser) {
            return pkg.getMonthlyPrice();
        }
        if (pkg.getMonthlyDiscountPrice() != null) {
            return pkg.getMonthlyDiscountPrice();
        }
        return pkg.getMonthlyPrice();
    }

    private String normalizeBillingCycle(String billingCycle) {
        if (billingCycle == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "billingCycle不能为空");
        }
        String v = billingCycle.trim().toLowerCase();
        if ("yearly".equals(v) || "year".equals(v) || "y".equals(v) || "annual".equals(v)) {
            return "YEARLY";
        }
        if ("monthly".equals(v) || "month".equals(v) || "m".equals(v)) {
            return "MONTHLY";
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "billingCycle不合法");
    }

    private String generateOrderNo(Long userId) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String userIdSuffix = String.format("%04d", userId % 10000);
        return "M" + timestamp + random + userIdSuffix;
    }

    private static class MembershipPayload {
        public Integer packageId;
        public String billingCycle;
    }
}
