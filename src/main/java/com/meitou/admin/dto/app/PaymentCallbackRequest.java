package com.meitou.admin.dto.app;

import lombok.Data;
import java.util.Map;

/**
 * 支付回调请求 DTO
 * 用于接收微信支付和支付宝支付的回调数据
 */
@Data
public class PaymentCallbackRequest {
    
    /**
     * 订单号
     */
    private String orderNo;
    
    /**
     * 第三方支付订单号
     */
    private String thirdPartyOrderNo;
    
    /**
     * 支付金额
     */
    private String amount;
    
    /**
     * 支付状态
     */
    private String status;
    
    /**
     * 回调原始数据（Map格式，用于签名验证）
     */
    private Map<String, String> rawData;
    
    /**
     * 签名
     */
    private String sign;
}

