package com.meitou.admin.dto.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 用户端登录响应 DTO
 */
@Data
public class UserLoginResponse {
    
    /**
     * Token
     */
    private String token;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 积分余额
     */
    private Integer balance;
    
    /**
     * 站点ID（多租户字段）
     */
    private Long siteId;

    private String avatarUrl;

    private String company;

    private String wechat;

    /**
     * 是否是新用户（true-是，false-否）
     */
    @JsonProperty("isNewUser")
    private boolean isNewUser;

    /**
     * 注册时间
     */
    private String createdAt;
}
