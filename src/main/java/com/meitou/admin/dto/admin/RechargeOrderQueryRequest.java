package com.meitou.admin.dto.admin;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 充值订单查询请求
 */
@Data
public class RechargeOrderQueryRequest {
    
    /**
     * 站点ID (必填)
     */
    private Long siteId;
    
    /**
     * 搜索关键词（手机号）
     */
    private String search;
    
    /**
     * 支付渠道：wechat, alipay, system
     */
    private String paymentType;

    /**
     * 订单状态：pending-待支付，paying-支付中，paid-已支付，cancelled-已取消，refunded-已退款，failed-支付失败
     */
    private String status;

    /**
     * 充值类型：points_recharge-算力充值，membership-会员购买/续费
     */
    private String productType;
    
    /**
     * 开始日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    /**
     * 结束日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    /**
     * 页码
     */
    private Integer page = 1;
    
    /**
     * 每页大小
     */
    private Integer size = 10;
}
