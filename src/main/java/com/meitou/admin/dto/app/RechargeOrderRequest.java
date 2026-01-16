package com.meitou.admin.dto.app;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 创建充值订单请求 DTO
 */
@Data
public class RechargeOrderRequest {
    
    /**
     * 充值金额（元）
     */
    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于0")
    private BigDecimal amount;
    
    /**
     * 支付方式：wechat-微信支付，alipay-支付宝支付
     */
    @NotBlank(message = "支付方式不能为空")
    private String paymentType;
}

