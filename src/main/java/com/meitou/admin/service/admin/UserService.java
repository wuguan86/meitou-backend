package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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
        return userMapper.selectPage(pageParam, wrapper);
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
            throw new RuntimeException("手机号不能为空");
        }

        // 检查手机号是否已存在
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, user.getPhone());
            phoneWrapper.eq(User::getDeleted, 0);
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new RuntimeException("手机号已存在");
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
                throw new RuntimeException("邮箱已存在");
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
    public User updateUser(Long id, User user) {
        User existing = getUserById(id);
        
        // 更新字段
        if (user.getEmail() != null) {
            // 检查邮箱是否被其他用户使用
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getEmail, user.getEmail());
            wrapper.ne(User::getId, id);
            wrapper.eq(User::getDeleted, 0);
            User other = userMapper.selectOne(wrapper);
            if (other != null) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            existing.setEmail(user.getEmail());
        }
        if (user.getUsername() != null) {
            existing.setUsername(user.getUsername());
        }
        if (user.getPhone() != null) {
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
        // 密码更新（如果提供）
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        userMapper.updateById(existing);
        return existing;
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
        int updatedRows = userMapper.incrementBalance(id, points, java.time.LocalDateTime.now());
        if (updatedRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        
        // 记录流水
        User updatedUser = userMapper.selectById(id);
        UserTransaction transaction = new UserTransaction();
        transaction.setUserId(id);
        transaction.setType("SYSTEM");
        transaction.setAmount(points);
        transaction.setBalanceAfter(updatedUser.getBalance());
        transaction.setDescription("系统赠送积分");
        transaction.setSiteId(siteId);
        userTransactionMapper.insert(transaction);
        
        return updatedUser;
    }
}

