package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.InvitationCode;
import com.meitou.admin.entity.InvitationCodeUsage;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserMembershipPeriod;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.InvitationCodeMapper;
import com.meitou.admin.mapper.InvitationCodeUsageMapper;
import com.meitou.admin.mapper.MembershipPackageMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserMembershipPeriodMapper;
import com.meitou.admin.service.app.PointsLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 管理端邀请码服务类
 */
@Service
@RequiredArgsConstructor
public class InvitationCodeService extends ServiceImpl<InvitationCodeMapper, InvitationCode> {
    
    private final InvitationCodeMapper codeMapper;
    private final InvitationCodeUsageMapper usageMapper;
    private final UserMapper userMapper;
    private final MembershipPackageMapper membershipPackageMapper;
    private final UserMembershipPeriodMapper userMembershipPeriodMapper;
    private final PointsLedgerService pointsLedgerService;
    
    /**
     * 获取邀请码列表（按站点分类）
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 邀请码列表
     */
    public List<InvitationCode> getCodes() {
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectList(wrapper);
    }
    
    /**
     * 获取指定站点的邀请码列表（管理后台使用）
     * 注意：调用此方法前，需要先设置 SiteContext.setSiteId(siteId)，
     * 这样多租户插件会自动添加 site_id 过滤条件
     * 
     * @param siteId 站点ID
     * @return 邀请码列表
     */
    public List<InvitationCode> getCodesBySiteId(Long siteId) {
        // 不在这里添加 siteId 条件，因为多租户插件会自动添加
        // 如果在这里添加，会导致 SQL 中出现重复的 site_id 条件
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectList(wrapper);
    }
    
    /**
     * 分页获取邀请码列表
     * 
     * @param page 分页对象
     * @param code 邀请码
     * @param channel 渠道
     * @param status 状态
     * @return 分页结果
     */
    public IPage<InvitationCode> getPage(Page<InvitationCode> page, String code, String channel, String status) {
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(code)) {
            wrapper.like(InvitationCode::getCode, code);
        }
        if (StringUtils.hasText(channel)) {
            wrapper.like(InvitationCode::getChannel, channel);
        }
        if (StringUtils.hasText(status) && !"all".equals(status)) {
            wrapper.eq(InvitationCode::getStatus, status);
        }
        
        wrapper.orderByDesc(InvitationCode::getCreatedAt);
        return codeMapper.selectPage(page, wrapper);
    }

    /**
     * 生成邀请码
     * 
     * @param count 生成数量
     * @param points 赠送积分
     * @param maxUses 最大使用次数
     * @param siteId 站点ID
     * @param channel 渠道
     * @param validStartDate 有效期开始
     * @param validEndDate 有效期结束
     * @param type 类型
     * @param packageId 套餐ID
     * @param duration 时长
     * @param durationUnit 时长单位
     * @return 生成的邀请码列表
     */
    public List<InvitationCode> generateCodes(Integer count, Integer points, Integer maxUses,
                                              Long siteId, String channel,
                                              LocalDate validStartDate, LocalDate validEndDate,
                                              String type, Integer packageId, Integer duration, String durationUnit) {
        List<InvitationCode> codes = new java.util.ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            InvitationCode code = new InvitationCode();
            // 生成随机邀请码
            code.setCode("INV" + generateRandomString(6).toUpperCase());
            code.setPoints(points);
            code.setMaxUses(maxUses);
            code.setUsedCount(0);
            code.setStatus("active");
            code.setSiteId(siteId);
            code.setChannel(channel != null ? channel : "默认渠道");
            code.setValidStartDate(validStartDate);
            code.setValidEndDate(validEndDate);
            
            // 设置新字段
            code.setType(StringUtils.hasText(type) ? type : "common");
            code.setPackageId(packageId);
            code.setDuration(duration);
            code.setDurationUnit(durationUnit);
            
            codeMapper.insert(code);
            codes.add(code);
        }
        
        return codes;
    }

    /**
     * 兑换邀请码
     * 
     * @param userId 用户ID
     * @param codeStr 邀请码
     * @return 兑换结果描述
     */
    @Transactional
    public String redeemCode(Long userId, String codeStr) {
        // 1. 查找邀请码
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvitationCode::getCode, codeStr);
        wrapper.eq(InvitationCode::getStatus, "active");
        InvitationCode code = codeMapper.selectOne(wrapper);
        
        if (code == null) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID);
        }

        // 2. 检查有效期
        LocalDate today = LocalDate.now();
        if (code.getValidStartDate() != null && today.isBefore(code.getValidStartDate())) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码尚未生效");
        }
        if (code.getValidEndDate() != null && today.isAfter(code.getValidEndDate())) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码已过期");
        }

        // 3. 检查使用次数
        if (code.getMaxUses() != null && code.getUsedCount() >= code.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码已达到最大使用次数");
        }

        // 3.5 检查用户是否已使用过该兑换码
        LambdaQueryWrapper<InvitationCodeUsage> usageWrapper = new LambdaQueryWrapper<>();
        usageWrapper.eq(InvitationCodeUsage::getUserId, userId);
        usageWrapper.eq(InvitationCodeUsage::getCodeId, code.getId());
        if (usageMapper.selectCount(usageWrapper) > 0) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "您已使用过此兑换码");
        }
        
        // 4. 更新使用次数
        code.setUsedCount(code.getUsedCount() + 1);
        codeMapper.updateById(code);
        
        // 4.5 记录使用记录
        InvitationCodeUsage usage = new InvitationCodeUsage();
        usage.setUserId(userId);
        usage.setCodeId(code.getId());
        usage.setSiteId(code.getSiteId());
        usage.setUsedAt(LocalDateTime.now());
        usageMapper.insert(usage);
        
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        StringBuilder resultMsg = new StringBuilder();

        // 5. 赠送积分
        if (code.getPoints() != null && code.getPoints() > 0) {
            user.setBalance(user.getBalance() + code.getPoints());
            userMapper.updateById(user);
            resultMsg.append("获得").append(code.getPoints()).append("积分");
        }
        
        // 6. 赠送会员
        if ("membership".equals(code.getType()) && code.getPackageId() != null && code.getDuration() != null) {
            MembershipPackage pkg = membershipPackageMapper.selectById(code.getPackageId());
            if (pkg == null) {
                throw new BusinessException(ErrorCode.MEMBERSHIP_PACKAGE_NOT_FOUND);
            }
            
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startAt;
            String initialStatus; // 整个批次的初始状态依据
            
            // 检查当前是否有相同等级的会员
            UserMembershipPeriod active = findActiveMembership(userId, now);
            if (active != null && active.getEndAt() != null && active.getEndAt().isAfter(now)) {
                if (active.getLevelCode() != null && !active.getLevelCode().equals(pkg.getLevelCode())) {
                    throw new BusinessException(ErrorCode.MEMBERSHIP_TYPE_SWITCH_NOT_ALLOWED.getCode(), "当前已有不同等级会员，无法兑换");
                }
                startAt = active.getEndAt();
                initialStatus = "scheduled";
            } else {
                startAt = now;
                initialStatus = "active";
            }
            
            String orderNo = "RED" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            
            int totalMonths = 0;
            String unit = code.getDurationUnit();
            if ("year".equalsIgnoreCase(unit)) {
                totalMonths = code.getDuration() * 12;
            } else if ("month".equalsIgnoreCase(unit)) {
                totalMonths = code.getDuration();
            }
            
            // 如果是按月/年计算，拆分周期
            if (totalMonths > 0) {
                LocalDateTime currentStart = startAt;
                for (int i = 0; i < totalMonths; i++) {
                    LocalDateTime currentEnd = currentStart.plusMonths(1);
                    
                    String currentStatus;
                    if ("scheduled".equals(initialStatus)) {
                        currentStatus = "scheduled";
                    } else {
                        currentStatus = (i == 0) ? "active" : "scheduled";
                    }
                    
                    createMembershipPeriod(userId, pkg, currentStart, currentEnd, currentStatus, orderNo);
                    
                    currentStart = currentEnd;
                }
            } else {
                // 如果是按天计算或其他非标准周期，作为一个整体周期
                LocalDateTime currentEnd = calculateEndTime(startAt, code.getDuration(), unit);
                createMembershipPeriod(userId, pkg, startAt, currentEnd, initialStatus, orderNo);
            }
            
            if (resultMsg.length() > 0) resultMsg.append("，");
            resultMsg.append("获得").append(pkg.getName()).append(code.getDuration()).append(getUnitName(code.getDurationUnit()));
        }
        
        return resultMsg.toString();
    }
    
    private void createMembershipPeriod(Long userId, MembershipPackage pkg, LocalDateTime startAt, LocalDateTime endAt, String status, String orderNo) {
        UserMembershipPeriod period = new UserMembershipPeriod();
        period.setUserId(userId);
        period.setPackageId(pkg.getId());
        period.setLevelCode(pkg.getLevelCode());
        period.setBillingCycle("redemption");
        period.setStartAt(startAt);
        period.setEndAt(endAt);
        period.setStatus(status);
        period.setOrderNo(orderNo);
        period.setSiteId(SiteContext.getSiteId());
        
        userMembershipPeriodMapper.insert(period);
        
        // 只有当前状态为 active 时才立即发放积分
        if ("active".equals(status)) {
            Integer reward = pkg.getPointsReward();
            if (reward != null && reward > 0) {
                pointsLedgerService.grantExpiringPoints(userId, reward, "MEMBERSHIP", orderNo, endAt,
                        "会员赠送算力-" + pkg.getLevelCode(), period.getId());
            }
        }
    }
    
    private LocalDateTime calculateEndTime(LocalDateTime start, Integer duration, String unit) {
        if ("day".equalsIgnoreCase(unit)) {
            return start.plusDays(duration);
        } else if ("month".equalsIgnoreCase(unit)) {
            return start.plusMonths(duration);
        } else if ("year".equalsIgnoreCase(unit)) {
            return start.plusYears(duration);
        }
        return start.plusDays(duration);
    }
    
    private String getUnitName(String unit) {
        if ("day".equalsIgnoreCase(unit)) return "天";
        if ("month".equalsIgnoreCase(unit)) return "个月";
        if ("year".equalsIgnoreCase(unit)) return "年";
        return "天";
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
    
    /**
     * 生成随机字符串
     * 
     * @param length 长度
     * @return 随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 更新邀请码
     * 
     * @param id 邀请码ID
     * @param code 邀请码信息
     * @return 更新后的邀请码
     */
    public InvitationCode updateCode(Long id, InvitationCode code) {
        InvitationCode existing = getCodeById(id);
        
        if (code.getStatus() != null) {
            existing.setStatus(code.getStatus());
        }
        if (code.getChannel() != null) {
            existing.setChannel(code.getChannel());
        }
        if (code.getPoints() != null) {
            existing.setPoints(code.getPoints());
        }
        if (code.getMaxUses() != null) {
            existing.setMaxUses(code.getMaxUses());
        }
        if (code.getValidStartDate() != null) {
            existing.setValidStartDate(code.getValidStartDate());
        }
        if (code.getValidEndDate() != null) {
            existing.setValidEndDate(code.getValidEndDate());
        }
        if (code.getType() != null) {
            existing.setType(code.getType());
        }
        if (code.getPackageId() != null) {
            existing.setPackageId(code.getPackageId());
        }
        if (code.getDuration() != null) {
            existing.setDuration(code.getDuration());
        }
        if (code.getDurationUnit() != null) {
            existing.setDurationUnit(code.getDurationUnit());
        }
        
        codeMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除邀请码
     *
     * @param id 邀请码ID
     */
    public void deleteCode(Long id) {
        codeMapper.deleteById(id);
    }
    
    /**
     * 根据ID获取邀请码
     * 
     * @param id 邀请码ID
     * @return 邀请码
     */
    public InvitationCode getCodeById(Long id) {
        InvitationCode code = codeMapper.selectById(id);
        if (code == null) {
            throw new RuntimeException("邀请码不存在");
        }
        return code;
    }
}

