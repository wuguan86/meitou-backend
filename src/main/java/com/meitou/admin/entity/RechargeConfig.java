package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 充值配置实体类
 * 对应数据库表：recharge_configs
 */
@Data
@TableName("recharge_configs")
public class RechargeConfig {
    
    /**
     * 配置ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 兑换比例（1元 = X算力）
     */
    @TableField("exchange_rate")
    private Integer exchangeRate;
    
    /**
     * 最低充值金额（元）
     */
    @TableField("min_amount")
    private Integer minAmount;
    
    /**
     * 充值选项列表（JSON格式，存储多个选项，每个选项包含 points 和 price）
     */
    @TableField("options_json")
    private String optionsJson;
    
    /**
     * 是否启用自定义金额：0-否，1-是
     */
    @TableField("allow_custom")
    private Boolean allowCustom;
    
    /**
     * 是否启用：0-否，1-是
     */
    @TableField("is_enabled")
    private Boolean isEnabled;
    
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

