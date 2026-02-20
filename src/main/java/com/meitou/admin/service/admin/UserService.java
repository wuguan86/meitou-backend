package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.PublishedContent;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.entity.UserMembershipPeriod;
import com.meitou.admin.entity.UserPointBucket;
import com.meitou.admin.mapper.*;
import com.meitou.admin.service.app.PointsLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端用户服务类
 * 处理用户相关的业务逻辑
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {
    
    private final UserMapper userMapper; // 用户Mapper
    private final UserTransactionMapper userTransactionMapper; // 用户流水Mapper
    private final BCryptPasswordEncoder passwordEncoder; // 密码编码器（通过依赖注入）
    private final PublishedContentMapper publishedContentMapper;
    private final PointsLedgerService pointsLedgerService;
    private final UserMembershipPeriodMapper userMembershipPeriodMapper;
    private final MembershipPackageMapper membershipPackageMapper;
    private final UserPointBucketMapper userPointBucketMapper;
    
    /**
     * 获取用户列表（支持站点ID和搜索，分页）
     * 管理后台需要查看所有站点的用户，所以不使用多租户过滤
     * 
     * @param siteId 站点ID（可选）
     * @param search 搜索关键词
     * @param page 当前页码
     * @param size 每页数量
     * @return 分页用户列表
     */
    public IPage<User> getUsers(Long siteId, String search, Integer page, Integer size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (siteId != null) {
            wrapper.eq(User::getSiteId, siteId);
        }
        if (StringUtils.hasText(search)) {
            wrapper.and(w -> w.like(User::getUsername, search)
                    .or().like(User::getEmail, search)
                    .or().like(User::getPhone, search));
        }
        wrapper.orderByDesc(User::getCreatedAt);
        
        Page<User> pageParam = new Page<>(page, size);
        IPage<User> result = userMapper.selectPage(pageParam, wrapper);

        // --- 填充扩展字段 (会员信息、积分明细) ---
        List<User> users = result.getRecords();
        if (users.isEmpty()) {
            return result;
        }

        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();

        // 1. 获取活跃会员周期
        // 查找条件：userId IN userIds AND status = 'active' AND end_at > now
        // 按照结束时间倒序排列，取最晚过期的
        LambdaQueryWrapper<UserMembershipPeriod> periodWrapper = new LambdaQueryWrapper<>();
        periodWrapper.in(UserMembershipPeriod::getUserId, userIds);
        periodWrapper.eq(UserMembershipPeriod::getStatus, "active");
        periodWrapper.gt(UserMembershipPeriod::getEndAt, now);
        if (siteId != null) {
            periodWrapper.eq(UserMembershipPeriod::getSiteId, siteId);
        }
        periodWrapper.orderByDesc(UserMembershipPeriod::getEndAt);

        List<UserMembershipPeriod> activePeriods = userMembershipPeriodMapper.selectList(periodWrapper);

        // Map: userId -> Period (保留结束时间最晚的一个)
        Map<Long, UserMembershipPeriod> userPeriodMap = activePeriods.stream()
                .collect(Collectors.toMap(
                        UserMembershipPeriod::getUserId,
                        p -> p,
                        (existing, replacement) -> existing
                ));

        // 获取套餐名称
        List<Integer> packageIds = activePeriods.stream()
                .map(UserMembershipPeriod::getPackageId)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, String> packageNameMap;
        if (!packageIds.isEmpty()) {
            List<MembershipPackage> packages = membershipPackageMapper.selectBatchIds(packageIds);
            packageNameMap = packages.stream()
                    .collect(Collectors.toMap(MembershipPackage::getId, MembershipPackage::getName));
        } else {
            packageNameMap = Map.of();
        }

        // 2. 获取积分桶明细
        // 查找条件：userId IN userIds AND deleted=0 AND status='active' AND remaining_points > 0
        // AND (expires_at IS NULL OR expires_at > now)
        LambdaQueryWrapper<UserPointBucket> bucketWrapper = new LambdaQueryWrapper<>();
        bucketWrapper.in(UserPointBucket::getUserId, userIds);
        bucketWrapper.eq(UserPointBucket::getDeleted, 0);
        bucketWrapper.eq(UserPointBucket::getStatus, "active");
        bucketWrapper.gt(UserPointBucket::getRemainingPoints, 0);
        bucketWrapper.and(w -> w.isNull(UserPointBucket::getExpiresAt).or().gt(UserPointBucket::getExpiresAt, now));
        if (siteId != null) {
            bucketWrapper.eq(UserPointBucket::getSiteId, siteId);
        }

        List<UserPointBucket> buckets = userPointBucketMapper.selectList(bucketWrapper);

        // Map: userId -> List<Bucket>
        Map<Long, List<UserPointBucket>> userBucketsMap = buckets.stream()
                .collect(Collectors.groupingBy(UserPointBucket::getUserId));

        // 3. 赋值给 User 对象
        for (User user : users) {
            // 会员信息
            UserMembershipPeriod period = userPeriodMap.get(user.getId());
            if (period != null) {
                user.setMembershipExpireAt(period.getEndAt());
                String pkgName = packageNameMap.get(period.getPackageId());
                user.setMembershipName(pkgName != null ? pkgName : period.getLevelCode());
            } else {
                user.setMembershipName("免费用户");
                user.setMembershipExpireAt(null);
            }

            // 积分明细
            List<UserPointBucket> userBuckets = userBucketsMap.getOrDefault(user.getId(), List.of());
            int membershipPoints = 0;
            int giftPoints = 0;
            int computePoints = 0;

            for (UserPointBucket bucket : userBuckets) {
                String source = bucket.getSourceType();
                if ("MEMBERSHIP".equals(source)) {
                    membershipPoints += bucket.getRemainingPoints();
                } else if ("SYSTEM".equals(source) || "INVITATION".equals(source) || "REGISTER_GIFT".equals(source)) {
                    giftPoints += bucket.getRemainingPoints();
                } else {
                    // RECHARGE, LEGACY, etc.
                    computePoints += bucket.getRemainingPoints();
                }
            }

            user.setBalanceMembership(membershipPoints);
            user.setBalanceGift(giftPoints);
            user.setBalanceCompute(computePoints);
        }
        
        return result;
    }
    
    /**
     * 根据ID获取用户
     * 
     * @param id 用户ID
     * @return 用户
     */
    public User getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }
    
    /**
     * 创建用户
     * 
     * @param user 用户信息
     * @return 创建的用户
     */
    public User createUser(User user) {
        // 手机号必填校验
        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "手机号不能为空");
        }

        // 检查手机号是否已存在
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, user.getPhone());
            phoneWrapper.eq(User::getDeleted, 0);
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new BusinessException(ErrorCode.USER_PHONE_EXISTS);
            }
        }

        // 处理邮箱
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            // 检查邮箱是否已存在
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getEmail, user.getEmail());
            wrapper.eq(User::getDeleted, 0);
            User existing = userMapper.selectOne(wrapper);
            if (existing != null) {
                throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
            }
        } else {
            user.setEmail(null); // 邮箱为空时设置为null
        }
        
        // 处理用户名
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            if (user.getPhone() != null && user.getPhone().length() >= 4) {
                user.setUsername("用户_" + user.getPhone().substring(user.getPhone().length() - 4));
            } else {
                user.setUsername("用户_" + System.currentTimeMillis() % 10000);
            }
        } else {
            // 检查用户名是否已存在
            LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
            usernameWrapper.eq(User::getUsername, user.getUsername());
            usernameWrapper.eq(User::getDeleted, 0);
            if (userMapper.selectCount(usernameWrapper) > 0) {
                throw new BusinessException(ErrorCode.USER_NAME_EXISTS);
            }
        }

        // 加密密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        // 设置默认值
        if (user.getBalance() == null) {
            user.setBalance(0);
        }
        if (user.getStatus() == null) {
            user.setStatus("active");
        }
        
        userMapper.insert(user);
        return user;
    }
    
    /**
     * 更新用户
     * 
     * @param id 用户ID
     * @param user 用户信息
     * @return 更新后的用户
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        String updatedUsername = null;
        String updatedAvatarUrl = null;
        
        // 更新字段
        if (user.getEmail() != null) {
            // 检查邮箱是否被其他用户使用
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getEmail, user.getEmail());
            wrapper.ne(User::getId, id);
            wrapper.eq(User::getDeleted, 0);
            User other = userMapper.selectOne(wrapper);
            if (other != null) {
                throw new BusinessException(ErrorCode.USER_EMAIL_EXISTS);
            }
            existing.setEmail(user.getEmail());
        }
        if (user.getUsername() != null) {
            // 检查用户名是否被其他用户使用
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, user.getUsername());
            wrapper.ne(User::getId, id);
            wrapper.eq(User::getDeleted, 0);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.USER_NAME_EXISTS);
            }
            existing.setUsername(user.getUsername());
            updatedUsername = user.getUsername();
        }
        if (user.getPhone() != null) {
            // 检查手机号是否被其他用户使用
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getPhone, user.getPhone());
            wrapper.ne(User::getId, id);
            wrapper.eq(User::getDeleted, 0);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.USER_PHONE_EXISTS);
            }
            existing.setPhone(user.getPhone());
        }
        if (user.getWechat() != null) {
            existing.setWechat(user.getWechat());
        }
        if (user.getCompany() != null) {
            existing.setCompany(user.getCompany());
        }
        if (user.getRole() != null) {
            existing.setRole(user.getRole());
        }
        if (user.getStatus() != null) {
            existing.setStatus(user.getStatus());
        }
        if (user.getSiteId() != null) {
            existing.setSiteId(user.getSiteId());
        }
        if (user.getAvatarUrl() != null) {
            existing.setAvatarUrl(user.getAvatarUrl());
            updatedAvatarUrl = user.getAvatarUrl();
        }
        // 密码更新（如果提供）
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        userMapper.updateById(existing);
        syncPublishedContentUserSnapshot(existing.getId(), updatedUsername, updatedAvatarUrl);
        return existing;
    }

    private void syncPublishedContentUserSnapshot(Long userId, String userName, String userAvatarUrl) {
        if (userId == null) {
            return;
        }
        boolean hasUpdate = false;
        LambdaUpdateWrapper<PublishedContent> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(PublishedContent::getUserId, userId);
        if (userName != null) {
            updateWrapper.set(PublishedContent::getUserName, userName);
            hasUpdate = true;
        }
        if (userAvatarUrl != null) {
            updateWrapper.set(PublishedContent::getUserAvatarUrl, userAvatarUrl);
            hasUpdate = true;
        }
        if (!hasUpdate) {
            return;
        }
        publishedContentMapper.update(null, updateWrapper);
    }
    
    /**
     * 删除用户（逻辑删除）
     * 
     * @param id 用户ID
     */
    public void deleteUser(Long id) {
        getUserById(id); // 检查用户是否存在
        userMapper.deleteById(id);
    }
    
    /**
     * 赠送积分
     * 
     * @param id 用户ID
     * @param points 积分数量
     * @param siteId 站点ID
     * @return 更新后的用户
     */
    @Transactional(rollbackFor = Exception.class)
    public User giftPoints(Long id, Integer points, Long siteId) {
        if (points == null || points <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "积分数量必须大于0");
        }

        // 防重校验：检查最近2秒内是否有相同金额的系统赠送记录
        LocalDateTime twoSecondsAgo = LocalDateTime.now().minusSeconds(2);
        LambdaQueryWrapper<UserTransaction> transactionWrapper = new LambdaQueryWrapper<>();
        transactionWrapper.eq(UserTransaction::getUserId, id)
                .eq(UserTransaction::getType, "SYSTEM")
                .eq(UserTransaction::getAmount, points)
                .ge(UserTransaction::getCreatedAt, twoSecondsAgo)
                .orderByDesc(UserTransaction::getCreatedAt)
                .last("LIMIT 1");
        
        if (userTransactionMapper.selectCount(transactionWrapper) > 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "操作太频繁，请稍后再试");
        }

        getUserById(id);
        pointsLedgerService.grantNonExpiringPoints(id, points, "SYSTEM", null, "系统赠送积分", null);

        return userMapper.selectById(id);
    }
}

