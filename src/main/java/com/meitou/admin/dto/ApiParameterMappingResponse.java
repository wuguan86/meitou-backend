package com.meitou.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * API参数映射响应DTO
 */
@Data
public class ApiParameterMappingResponse {
    
    private Long id;
    private Long siteId;
    private Long platformId;
    private String platformName; // 额外字段，方便前端显示
    private String modelName;
    private Integer mappingType;
    private String paramLocation;
    private Boolean isRequired;
    private String internalParam;
    private String targetParam;
    private String fixedValue;
    private String internalValue;
    private String targetValue;
    private String paramType;
    private String defaultValue;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
