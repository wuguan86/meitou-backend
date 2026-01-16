package com.meitou.admin.controller.admin;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.LoginRequest;
import com.meitou.admin.dto.LoginResponse;
import com.meitou.admin.service.admin.AuthAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端认证控制器
 * 处理后台管理登录、登出等认证相关接口
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthAdminController {
    
    private final AuthAdminService authAdminService;
    
    /**
     * 登录接口
     * 
     * @param request 登录请求
     * @return 登录响应（包含Token）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authAdminService.login(request);
        return Result.success("登录成功", response);
    }
    
    /**
     * 登出接口
     * 
     * @return 登出结果
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 简单实现：实际项目中应该清除Token
        return Result.success("登出成功");
    }
    
    /**
     * 检查登录状态
     * 
     * @param token Token
     * @return 是否有效
     */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        boolean valid = authAdminService.checkToken(token);
        return Result.success(valid);
    }
}

