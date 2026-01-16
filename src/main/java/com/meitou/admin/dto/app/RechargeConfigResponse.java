package com.meitou.admin.dto.app;

import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 充值配置响应 DTO
 */
@Data
public class RechargeConfigResponse {
    
    /**
     * 兑换比例（1元 = X算力）
     */
    private Integer exchangeRate;
    
    /**
     * 最低充值金额（元）
     */
    private Integer minAmount;
    
    /**
     * 充值选项列表
     */
    private List<RechargeOption> options;
    
    /**
     * 是否启用自定义金额
     */
    private Boolean allowCustom;

    /**
     * 启用的支付方式列表
     */
    private List<String> enabledPaymentMethods;
    
    /**
     * 对公转账信息（JSON字符串或对象）
     */
    private BankInfo bankInfo;
    
    /**
     * 充值选项内部类
     */
    @Data
    public static class RechargeOption {
        /**
         * 算力点数
         */
        private Integer points;
        
        /**
         * 价格（元）
         */
        private Integer price;
    }

    /**
     * 银行信息内部类
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankInfo {
        private String bankName;      // 开户银行
        private String accountName;   // 账户名称
        
        @JsonProperty("bankAccount")
        private String accountNumber; // 银行账号
    }
}

