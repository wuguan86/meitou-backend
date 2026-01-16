package com.meitou.admin.dto;

import lombok.Data;

/**
 * API参数映射请求DTO
 */
@Data
public class ApiParameterMappingRequest {
    
    /**
     * 站点ID（多租户字段）
     */
    private Long siteId;

    /**
     * 平台ID
     */
    private Long platformId;

    /**
     * 模型名称（为空则通用）
     */
    private String modelName;

    /**
     * 映射类型: 1-字段映射, 2-固定值
     */
    private Integer mappingType;

    /**
     * 参数位置: body, query, header
     */
    private String paramLocation;

    /**
     * 是否必填
     */
    private Boolean isRequired;

    /**
     * 内部参数名
     */
    private String internalParam;

    /**
     * 目标参数名
     */
    private String targetParam;

    /**
     * 固定值
     */
    private String fixedValue;

    /**
     * 内部参数值（用于枚举转换）
     */
    private String internalValue;

    /**
     * 目标参数值（用于枚举转换）
     */
    private String targetValue;

    /**
     * 目标参数类型：string, integer, boolean, json
     */
    private String paramType;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 描述
     */
    private String description;
}
