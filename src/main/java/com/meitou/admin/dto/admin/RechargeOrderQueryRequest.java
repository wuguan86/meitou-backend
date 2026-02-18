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
