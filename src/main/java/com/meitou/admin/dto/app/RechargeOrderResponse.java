package com.meitou.admin.dto.app;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单响应 DTO
 */
@Data
public class RechargeOrderResponse {
    
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
     * 支付参数（JSON字符串，包含二维码URL或支付链接等）
     */
    private String paymentParams;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}

