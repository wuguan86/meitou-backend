package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表：users
 * Updated for prompt optimization feature
 */
@Data
@TableName("users")
public class User {
    
    /**
     * 用户ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 邮箱（唯一）
     */
    private String email;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码（加密存储）
     */
    private String password;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 微信号
     */
    private String wechat;
    
    /**
     * 公司/机构
     */
    private String company;
    
    /**
     * 角色
     */
    private String role;
    
    /**
     * 积分余额
     */
    private Integer balance;
    
    /**
     * 状态：active-正常，suspended-停用
     */
    private String status;
    
    /**
     * 用户头像URL
     */
    @TableField("avatar_url")
    private String avatarUrl;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;
}

