package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("api_parameter_mappings")
public class ApiParameterMapping {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("site_id")
    private Long siteId;

    @TableField("platform_id")
    private Long platformId;

    @TableField("model_name")
    private String modelName;

    @TableField("mapping_type")
    private Integer mappingType; // 1-字段映射, 2-固定值

    @TableField("param_location")
    private String paramLocation; // body, query, header

    @TableField("is_required")
    private Boolean isRequired;

    @TableField("internal_param")
    private String internalParam;

    @TableField("target_param")
    private String targetParam;

    @TableField("fixed_value")
    private String fixedValue;

    @TableField("internal_value")
    private String internalValue;

    @TableField("target_value")
    private String targetValue;

    @TableField("param_type")
    private String paramType; // string, integer, boolean, json

    @TableField("default_value")
    private String defaultValue;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}