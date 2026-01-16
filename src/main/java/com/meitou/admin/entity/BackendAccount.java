package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 后台账号实体类
 * 对应数据库表：backend_accounts
 */
@Data
@TableName("backend_accounts")
public class BackendAccount {
    
    /**
     * 账号ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 邮箱账号（唯一）
     */
    private String email;
    
    /**
     * 密码（加密存储）
     */
    private String password;
    
    /**
     * 角色权限：admin-超级管理员，operator-运营人员，viewer-访客
     */
    private String role;
    
    /**
     * 状态：active-正常，locked-已锁定
     */
    private String status;
    
    /**
     * 最后登录时间
     */
    @TableField("last_login")
    private LocalDateTime lastLogin;
    
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
    
    /**
     * 站点ID（多租户字段，0表示全局账号）
     */
    @TableField(value = "site_id", fill = FieldFill.INSERT)
    private Long siteId;
}

