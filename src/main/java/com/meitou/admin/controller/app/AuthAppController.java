package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.ChangePasswordRequest;
import com.meitou.admin.dto.app.CodeLoginRequest;
import com.meitou.admin.dto.app.PasswordLoginRequest;
import com.meitou.admin.dto.app.SendCodeRequest;
import com.meitou.admin.dto.app.SetPasswordRequest;
import com.meitou.admin.dto.app.UserLoginResponse;
import com.meitou.admin.service.app.AuthAppService;
import com.meitou.admin.service.app.SmsCodeService;
import com.meitou.admin.storage.FileStorageService;
import com.meitou.admin.util.TokenUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端认证控制器
 * 处理用户验证码登录、发送验证码等接口
 */
@RestController
@RequestMapping("/api/app/auth")
@RequiredArgsConstructor
public class AuthAppController {
    
    private final AuthAppService authAppService;
    private final SmsCodeService smsCodeService;
    private final FileStorageService fileStorageService;
    
    /**
     * 发送验证码接口
     * 
     * @param request 发送验证码请求
     * @return 发送结果
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        // 检查是否已有有效验证码（防止频繁发送）
        if (smsCodeService.hasValidCode(request.getPhone())) {
            return Result.error("验证码已发送，请勿频繁请求");
        }
        
        // 发送验证码
        smsCodeService.sendCode(request.getPhone());
        
        return Result.success("验证码发送成功");
    }
    
    /**
     * 验证码登录接口
     * 
     * @param request 登录请求
     * @return 登录响应（包含Token和用户信息）
     */
    @PostMapping("/login-by-code")
    public Result<UserLoginResponse> loginByCode(@Valid @RequestBody CodeLoginRequest request) {
        UserLoginResponse response = authAppService.loginByCode(request);
        // 对头像URL进行签名
        if (response != null && StringUtils.hasText(response.getAvatarUrl())) {
            response.setAvatarUrl(fileStorageService.getFileUrl(response.getAvatarUrl()));
        }
        return Result.success("登录成功", response);
    }

    @PostMapping("/login-by-password")
    public Result<UserLoginResponse> loginByPassword(@Valid @RequestBody PasswordLoginRequest request) {
        UserLoginResponse response = authAppService.loginByPassword(request);
        // 对头像URL进行签名
        if (response != null && StringUtils.hasText(response.getAvatarUrl())) {
            response.setAvatarUrl(fileStorageService.getFileUrl(response.getAvatarUrl()));
        }
        return Result.success("登录成功", response);
    }

    /**
     * 设置密码接口
     * 
     * @param request 设置密码请求
     * @param token Token
     * @return 结果
     */
    @PostMapping("/set-password")
    public Result<Void> setPassword(@Valid @RequestBody SetPasswordRequest request,
                                   @RequestHeader("Authorization") String token) {
        Long userId = TokenUtil.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error("未登录或Token无效");
        }
        
        authAppService.setPassword(userId, request.getPassword());
        return Result.success("密码设置成功");
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String token
    ) {
        Long userId = TokenUtil.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error("未登录或Token无效");
        }

        authAppService.changePassword(userId, request.getOldPassword(), request.getNewPassword(), request.getCode());
        return Result.success("密码修改成功");
    }
    
    /**
     * 获取当前用户信息接口
     * 
     * @param token Token（从请求头获取）
     * @return 用户信息
     */
    @GetMapping("/me")
    public Result<UserLoginResponse> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // 从Token中提取用户ID
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            
            UserLoginResponse response = authAppService.getCurrentUser(userId);
            
            // 对头像URL进行签名
            if (response != null && StringUtils.hasText(response.getAvatarUrl())) {
                response.setAvatarUrl(fileStorageService.getFileUrl(response.getAvatarUrl()));
            }
            
            return Result.success("获取成功", response);
        } catch (Exception e) {
            return Result.error("获取用户信息失败：" + e.getMessage());
        }
    }
}
