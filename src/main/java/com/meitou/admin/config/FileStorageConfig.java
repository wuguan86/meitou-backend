package com.meitou.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageConfig {
    
    /**
     * 存储类型：tencent, aliyun
     */
    private String type;
    
    /**
     * 阿里云配置
     */
    private AliyunConfig aliyun;
    
    /**
     * 腾讯云配置
     */
    private TencentConfig tencent;
    
    @Data
    public static class AliyunConfig {
        private String accessKeyId;
        private String accessKeySecret;
        private String endpoint;
        private String bucketName;
        private String domain;
    }
    
    @Data
    public static class TencentConfig {
        private String secretId;
        private String secretKey;
        private String region;
        private String bucketName;
        private String domain;
    }
}
