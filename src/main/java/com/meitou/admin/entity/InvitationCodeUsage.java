package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 邀请码使用记录实体类
 * 对应数据库表：invitation_code_usages
 */
@Data
@TableName("invitation_code_usages")
public class InvitationCodeUsage {
    
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 邀请码ID
     */
    @TableField("code_id")
    private Long codeId;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 使用时间
     */
    @TableField("used_at")
    private LocalDateTime usedAt;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
