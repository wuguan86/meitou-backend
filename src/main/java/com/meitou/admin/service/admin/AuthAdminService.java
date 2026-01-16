package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meitou.admin.dto.LoginRequest;
import com.meitou.admin.dto.LoginResponse;
import com.meitou.admin.entity.BackendAccount;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.BackendAccountMapper;
import com.meitou.admin.service.common.LoginAttemptService;
import com.meitou.admin.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理端认证服务类
 * 处理后台管理登录、登出等认证相关业务
 */
@Service
@RequiredArgsConstructor
public class AuthAdminService {
    
    private final BackendAccountMapper accountMapper; // 账号Mapper
    private final BCryptPasswordEncoder passwordEncoder; // 密码编码器（通过依赖注入）
    private final LoginAttemptService loginAttemptService;
    
    /**
     * 登录
     * 
     * @param request 登录请求
     * @return 登录响应（包含Token）
     */
    public LoginResponse login(LoginRequest request) {
        // 检查锁定
        if (loginAttemptService.isLocked(request.getAccount())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 查询账号
        LambdaQueryWrapper<BackendAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BackendAccount::getEmail, request.getAccount());
        wrapper.eq(BackendAccount::getDeleted, 0);
        BackendAccount account = accountMapper.selectOne(wrapper);
        
        if (account == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            loginAttemptService.loginFailed(request.getAccount());
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        
        // 登录成功
        loginAttemptService.loginSucceeded(request.getAccount());

        // 检查账号状态
        if (!"active".equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        
        // 更新最后登录时间
        account.setLastLogin(LocalDateTime.now());
        accountMapper.updateById(account);
        
        // 生成 JWT Token (类型为 admin)
        String token = TokenUtil.generateToken(account.getId(), "admin");
        
        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setEmail(account.getEmail());
        response.setRole(account.getRole());
        
        return response;
    }
    
    /**
     * 检查登录状态
     * 
     * @param token Token
     * @return 是否有效
     */
    public boolean checkToken(String token) {
        // 验证 Token 是否有效且类型为 admin
        return TokenUtil.isValidToken(token) && "admin".equals(TokenUtil.getUserTypeFromToken(token));
    }
}
