package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 手册配置实体类
 * 对应数据库表：manual_configs
 */
@Data
@TableName("manual_configs")
public class ManualConfig {
    
    /**
     * 手册ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 站点ID（多租户字段，唯一）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 手册标题
     */
    private String title;
    
    /**
     * 手册URL
     */
    private String url;
    
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

