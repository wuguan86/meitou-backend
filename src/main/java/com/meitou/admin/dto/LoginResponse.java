package com.meitou.admin.dto;

import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
public class LoginResponse {
    
    /**
     * Token
     */
    private String token;
    
    /**
     * 账号邮箱
     */
    private String email;
    
    /**
     * 角色
     */
    private String role;
}

