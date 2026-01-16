package com.meitou.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付配置属性类
 * 从application.yml中读取支付相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    
    /**
     * 微信支付配置
     */
    private WechatPayConfig wechat = new WechatPayConfig();
    
    /**
     * 支付宝支付配置
     */
    private AlipayConfig alipay = new AlipayConfig();
    
    @Data
    public static class WechatPayConfig {
        /**
         * 微信支付AppID
         */
        private String appId;
        
        /**
         * 微信支付商户号
         */
        private String mchId;
        
        /**
         * 微信支付商户密钥
         */
        private String mchKey;
        
        /**
         * 微信支付APIv3密钥（用于回调验证）
         */
        private String apiV3Key;
        
        /**
         * 证书序列号（用于APIv3）
         */
        private String certSerialNo;

        /**
         * 微信支付平台公钥ID（公钥模式）
         */
        private String wechatPayPublicKeyId;

        /**
         * 微信支付平台公钥内容（PEM格式，公钥模式）
         */
        private String wechatPayPublicKey;
        
        /**
         * 证书文件路径（.p12格式）
         */
        private String certPath;

        /**
         * API私钥内容（PEM格式）
         */
        private String privateKey;

        /**
         * API证书内容（PEM格式）
         */
        private String certContent;
        
        /**
         * 支付回调地址
         */
        private String notifyUrl;
        
        /**
         * 是否使用沙箱环境
         */
        private Boolean useSandbox = false;
    }
    
    @Data
    public static class AlipayConfig {
        /**
         * 支付宝应用ID
         */
        private String appId;
        
        /**
         * 商户私钥（RSA2格式）
         */
        private String privateKey;
        
        /**
         * 支付宝公钥（用于验证回调签名）
         */
        private String alipayPublicKey;
        
        /**
         * 应用公钥证书内容（PEM格式）
         */
        private String appCertContent;
        
        /**
         * 支付宝根证书内容（PEM格式）
         */
        private String alipayRootCertContent;
        
        /**
         * 支付宝公钥证书内容（PEM格式）
         */
        private String alipayCertContent;
        
        /**
         * 支付回调地址
         */
        private String notifyUrl;
        
        /**
         * 支付完成同步跳转地址
         */
        private String returnUrl;
        
        /**
         * 支付宝网关地址
         */
        private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
        
        /**
         * 签名类型（固定为RSA2）
         */
        private String signType = "RSA2";
        
        /**
         * 字符编码（固定为UTF-8）
         */
        private String charset = "UTF-8";
        
        /**
         * 数据格式（固定为JSON）
         */
        private String format = "JSON";
    }
}

