package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 菜单配置实体类
 * 对应数据库表：menu_configs
 */
@Data
@TableName("menu_configs")
public class MenuConfig {
    
    /**
     * 菜单ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 菜单标签
     */
    private String label;
    
    /**
     * 菜单代码
     */
    private String code;
    
    /**
     * 是否可见：0-否，1-是
     */
    @TableField("is_visible")
    private Boolean isVisible;
    
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

