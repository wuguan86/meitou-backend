package com.meitou.admin.dto.app;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单查询响应 DTO
 */
@Data
public class OrderQueryResponse {
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 充值金额（元）
     */
    private BigDecimal amount;
    
    /**
     * 充值算力（积分）
     */
    private Integer points;
    
    /**
     * 支付方式
     */
    private String paymentType;
    
    /**
     * 订单状态
     */
    private String status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 支付时间
     */
    private LocalDateTime paidAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}

