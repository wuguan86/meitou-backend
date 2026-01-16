package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户余额流水实体类
 * 对应数据库表：user_transactions
 */
@Data
@TableName("user_transactions")
public class UserTransaction {
    
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 交易类型：
     * CONSUME-消费(生图/视频等)
     * REFUND-退款(失败退回)
     * RECHARGE-充值
     * SYSTEM-系统调整
     */
    private String type;
    
    /**
     * 变动金额（正数表示增加，负数表示减少）
     */
    private Integer amount;
    
    /**
     * 变动后余额
     */
    @TableField("balance_after")
    private Integer balanceAfter;
    
    /**
     * 关联ID（如生成记录ID、订单ID）
     */
    @TableField("reference_id")
    private Long referenceId;
    
    /**
     * 描述/备注
     */
    private String description;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
