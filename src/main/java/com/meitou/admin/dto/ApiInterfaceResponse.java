package com.meitou.admin.dto;

import lombok.Data;

/**
 * API接口响应DTO
 */
@Data
public class ApiInterfaceResponse {
    /**
     * 接口ID
     */
    private Long id;
    
    /**
     * 接口URL
     */
    private String url;
    
    /**
     * 请求方法
     */
    private String method;
    
    /**
     * 响应模式
     */
    private String responseMode;
    
    /**
     * 请求头配置（JSON字符串）
     */
    private String headers;
    
    /**
     * 参数配置（JSON字符串）
     */
    private String parametersJson;
    
    /**
     * 参数文档（JSON字符串）
     */
    private String paramDocs;
}

