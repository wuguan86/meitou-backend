package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meitou.admin.common.Constants;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.dto.app.CodeLoginRequest;
import com.meitou.admin.dto.app.PasswordLoginRequest;
import com.meitou.admin.dto.app.UserLoginResponse;
import com.meitou.admin.entity.InvitationCode;
import com.meitou.admin.entity.User;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.InvitationCodeMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.util.PasswordValidator;
import com.meitou.admin.util.TokenUtil;
import com.meitou.admin.service.common.LoginAttemptService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户端认证服务
 * 处理用户验证码登录、注册等业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAppService {
    
    private final UserMapper userMapper;
    private final InvitationCodeMapper invitationCodeMapper;
    private final SmsCodeService smsCodeService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate;

    private static final String NO_PASSWORD_PLACEHOLDER = "NO_PASSWORD_CODE_LOGIN";
    
    /**
     * 验证码登录
     * 如果用户不存在，则自动注册；如果存在，则直接登录
     * 
     * @param request 登录请求
     * @return 登录响应
     */
    @Transactional
    public UserLoginResponse loginByCode(CodeLoginRequest request) {
        // 验证验证码
        if (!smsCodeService.verifyCode(request.getPhone(), request.getCode())) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_ERROR);
        }
        
        // 查询用户是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, request.getPhone());
        wrapper.eq(User::getDeleted, 0);
        User user = userMapper.selectOne(wrapper);
        
        boolean isNewUser = false;

        // 如果用户不存在，自动注册或恢复已删除用户
        if (user == null) {
            // 检查是否存在已删除用户
            User deletedUser = userMapper.selectByPhoneIncludeDeleted(request.getPhone());
            if (deletedUser != null) {
                // 恢复已删除用户
                restoreDeletedUser(deletedUser, request.getInvitationCode());
                user = deletedUser;
                isNewUser = true;
            } else {
                user = createNewUser(request.getPhone(), request.getInvitationCode());
                isNewUser = true;
            }
        } else {
            // 检查用户状态
            if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
            // 如果用户已存在且尝试使用邀请码，提示无效
            if (request.getInvitationCode() != null && !request.getInvitationCode().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "无效的邀请码：仅限新用户使用");
            }
            
            // 如果用户存在但密码是默认值（说明之前未设置密码），也视为新用户处理
            if (NO_PASSWORD_PLACEHOLDER.equals(user.getPassword())) {
                isNewUser = true;
            }
        }
        
        // 生成Token (JWT)
        String token = TokenUtil.generateToken(user.getId(), "user");
        
        // 构建响应
        UserLoginResponse response = new UserLoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setBalance(user.getBalance() != null ? user.getBalance() : 0);
        response.setSiteId(user.getSiteId());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setCompany(user.getCompany());
        response.setWechat(user.getWechat());
        response.setNewUser(isNewUser);
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().toString());
        }
        
        return response;
    }

    public UserLoginResponse loginByPassword(PasswordLoginRequest request) {
        // 检查账号是否被锁定
        if (loginAttemptService.isLocked(request.getPhone())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, request.getPhone());
        wrapper.eq(User::getDeleted, 0);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), "账号不存在，请使用验证码登录注册");
        }
        if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getPassword() == null || NO_PASSWORD_PLACEHOLDER.equals(user.getPassword())) {
            throw new BusinessException(ErrorCode.NOT_SET_PASSWORD.getCode(), "尚未设置密码，请使用验证码登录后设置密码");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.loginFailed(request.getPhone());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        
        // 登录成功，重置失败次数
        loginAttemptService.loginSucceeded(request.getPhone());

        String token = TokenUtil.generateToken(user.getId(), "user");

        UserLoginResponse response = new UserLoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setBalance(user.getBalance() != null ? user.getBalance() : 0);
        response.setSiteId(user.getSiteId());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setCompany(user.getCompany());
        response.setWechat(user.getWechat());
        response.setNewUser(false);
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().toString());
        }

        return response;
    }
    
    /**
     * 创建新用户
     * 
     * @param phone 手机号
     * @param invitationCode 邀请码（可选）
     * @return 新创建的用户
     */
    private User createNewUser(String phone, String invitationCode) {
        // 从SiteContext获取当前站点ID
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new RuntimeException("无法识别站点，请检查请求头或域名配置");
        }
        
        User user = new User();
        user.setPhone(phone);
        user.setUsername("用户_" + phone.substring(7)); // 默认用户名为手机号后4位
        // 邮箱为空（修改为允许为NULL，不再生成假邮箱）
        user.setEmail(null);
        // 密码设置为默认值（数据库要求NOT NULL，验证码登录无需密码）
        // 使用一个默认的占位符，实际验证码登录不需要验证密码
        user.setPassword(NO_PASSWORD_PLACEHOLDER);
        user.setRole("user"); // 默认角色
        user.setBalance(0); // 默认积分为0
        user.setStatus(Constants.USER_STATUS_ACTIVE); // 默认状态为正常
        user.setSiteId(siteId); // 设置站点ID
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        
        // 如果有邀请码，处理邀请码逻辑
        if (invitationCode != null && !invitationCode.trim().isEmpty()) {
            handleInvitationCode(invitationCode, user);
        }
        
        // 插入用户
        userMapper.insert(user);
        
        // 生成并上传默认头像
        generateAndUploadAvatar(user);
        
        return user;
    }
    
    /**
     * 恢复已删除用户并重置为新用户状态
     * 
     * @param user 已删除的用户对象
     * @param invitationCode 邀请码
     */
    private void restoreDeletedUser(User user, String invitationCode) {
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new RuntimeException("无法识别站点，请检查请求头或域名配置");
        }
        
        user.setDeleted(0);
        user.setBalance(0);
        user.setPassword(NO_PASSWORD_PLACEHOLDER);
        user.setSiteId(siteId);
        user.setStatus(Constants.USER_STATUS_ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        // 重置用户名和邮箱
        user.setUsername("用户_" + user.getPhone().substring(7));
        user.setEmail(user.getPhone() + "@meitou.com");
        
        // 如果头像为空，生成默认头像
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
            generateAndUploadAvatar(user);
        }
        
        // 如果有邀请码，处理邀请码逻辑
        if (invitationCode != null && !invitationCode.trim().isEmpty()) {
            handleInvitationCode(invitationCode, user);
        }
        
        // 使用自定义方法更新（因为MyBatis Plus逻辑删除插件会阻止更新已删除记录）
        userMapper.restoreUser(user);
    }

    /**
     * 生成并上传默认头像
     * 
     * @param user 用户对象
     */
    private void generateAndUploadAvatar(User user) {
        try {
            // DiceBear API URL (使用SVG格式)
            String diceBearUrl = "https://api.dicebear.com/7.x/avataaars/svg?seed=" + user.getId();
            
            // 下载 SVG 内容
            ResponseEntity<byte[]> response = restTemplate.getForEntity(diceBearUrl, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] svgBytes = response.getBody();
                
                // 上传到 OSS
                try (InputStream inputStream = new ByteArrayInputStream(svgBytes)) {
                    String fileName = "avatar_" + user.getId() + "_" + System.currentTimeMillis() + ".svg";
                    String avatarUrl = fileStorageService.upload(inputStream, "avatars/", fileName);
                    
                    // 更新用户头像 URL
                    user.setAvatarUrl(avatarUrl);
                    // 注意：如果是 createNewUser 调用，这里会再次更新；
                    // 如果是 restoreDeletedUser 调用，这里更新的是内存对象，后续 restoreUser 会保存到数据库（但 restoreUser 可能不更新 avatarUrl 字段，需要检查）
                    
                    // 对于 createNewUser，userMapper.insert 后已经有 ID，这里 updateById 是安全的
                    if (user.getId() != null) {
                         userMapper.updateById(user);
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成并上传头像失败: userId={}", user.getId(), e);
            // 失败不影响注册流程，用户可以稍后自己上传
        }
    }
    
    /**
     * 处理邀请码逻辑
     * 
     * @param code 邀请码
     * @param user 新用户
     */
    private void handleInvitationCode(String code, User user) {
        // 查询邀请码
        LambdaQueryWrapper<InvitationCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvitationCode::getCode, code);
        wrapper.eq(InvitationCode::getStatus, "active"); // 状态为激活
        InvitationCode invitationCode = invitationCodeMapper.selectOne(wrapper);
        
        if (invitationCode == null) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID);
        }

        // 检查有效期
        LocalDate now = LocalDate.now();
        if (invitationCode.getValidStartDate() != null && now.isBefore(invitationCode.getValidStartDate())) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码尚未生效");
        }
        if (invitationCode.getValidEndDate() != null && now.isAfter(invitationCode.getValidEndDate())) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码已过期");
        }

        // 检查使用次数
        if (invitationCode.getMaxUses() != null && invitationCode.getUsedCount() >= invitationCode.getMaxUses()) {
            throw new BusinessException(ErrorCode.INVITATION_CODE_INVALID.getCode(), "邀请码已达到最大使用次数");
        }

        // 增加邀请码使用次数
        invitationCode.setUsedCount(invitationCode.getUsedCount() + 1);
        invitationCodeMapper.updateById(invitationCode);
        
        // 给新用户赠送积分
        if (invitationCode.getPoints() != null && invitationCode.getPoints() > 0) {
            user.setBalance(user.getBalance() + invitationCode.getPoints());
        }
    }
    
    /**
     * 获取当前用户信息
     * 
     * @param userId 用户ID
     * @return 用户登录响应
     */
    public UserLoginResponse getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查用户状态
        if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new RuntimeException("账号已被停用");
        }
        
        // 构建响应
        UserLoginResponse response = new UserLoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setBalance(user.getBalance() != null ? user.getBalance() : 0);
        response.setSiteId(user.getSiteId()); // 设置站点ID
        response.setAvatarUrl(user.getAvatarUrl());
        response.setCompany(user.getCompany());
        response.setWechat(user.getWechat());
        if (user.getCreatedAt() != null) {
            response.setCreatedAt(user.getCreatedAt().toString());
        }
        
        return response;
    }
    
    /**
     * 设置密码
     * 
     * @param userId 用户ID
     * @param password 新密码
     */
    @Transactional
    public void setPassword(Long userId, String password) {
        if (!PasswordValidator.validate(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 加密密码
        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);
        user.setUpdatedAt(LocalDateTime.now());
        
        userMapper.updateById(user);
    }

    @Transactional
    public UserLoginResponse updateProfile(Long userId, String email, String username, String company) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new RuntimeException("账号已被停用");
        }

        if (email != null && !email.trim().isEmpty()) {
            String normalizedEmail = email.trim();
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, normalizedEmail);
            emailWrapper.eq(User::getDeleted, 0);
            emailWrapper.ne(User::getId, userId);
            User existingUser = userMapper.selectOne(emailWrapper);
            if (existingUser != null) {
                throw new RuntimeException("邮箱已被占用");
            }
            user.setEmail(normalizedEmail);
        }

        if (username != null && !username.trim().isEmpty()) {
            user.setUsername(username.trim());
        }
        if (company != null) {
            user.setCompany(company.trim());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return getCurrentUser(userId);
    }

    @Transactional
    public void updateAvatarUrl(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new RuntimeException("账号已被停用");
        }
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String code) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        if (!Constants.USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new RuntimeException("账号已被停用");
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new RuntimeException("新密码不能为空");
        }
        if (!PasswordValidator.validate(newPassword)) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }

        // 如果提供了验证码，优先使用验证码验证
        if (code != null && !code.trim().isEmpty()) {
            if (!smsCodeService.verifyCode(user.getPhone(), code)) {
                throw new BusinessException(ErrorCode.VERIFY_CODE_ERROR);
            }
        } else {
            // 否则使用旧密码验证
            boolean hasRealPassword = user.getPassword() != null && !NO_PASSWORD_PLACEHOLDER.equals(user.getPassword());
            if (hasRealPassword) {
                if (oldPassword == null || oldPassword.trim().isEmpty()) {
                    throw new RuntimeException("旧密码不能为空");
                }
                if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                    throw new RuntimeException("旧密码错误");
                }
            }
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
