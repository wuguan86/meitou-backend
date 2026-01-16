package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单实体类
 * 对应数据库表：recharge_orders
 */
@Data
@TableName("recharge_orders")
public class RechargeOrder {
    
    /**
     * 订单ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 订单号（唯一）
     */
    @TableField("order_no")
    private String orderNo;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 充值金额（元）
     */
    private BigDecimal amount;
    
    /**
     * 充值算力（积分）
     */
    private Integer points;
    
    /**
     * 支付方式：wechat-微信支付，alipay-支付宝支付
     */
    @TableField("payment_type")
    private String paymentType;
    
    /**
     * 订单状态：pending-待支付，paying-支付中，paid-已支付，cancelled-已取消，refunded-已退款，failed-支付失败
     */
    private String status;
    
    /**
     * 第三方支付订单号
     */
    @TableField("third_party_order_no")
    private String thirdPartyOrderNo;
    
    /**
     * 支付回调信息（JSON格式）
     */
    @TableField("callback_info")
    private String callbackInfo;
    
    /**
     * 支付时间
     */
    @TableField("paid_at")
    private LocalDateTime paidAt;
    
    /**
     * 完成时间
     */
    @TableField("completed_at")
    private LocalDateTime completedAt;
    
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
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;
}

