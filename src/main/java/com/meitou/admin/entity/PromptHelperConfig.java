package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 提示词助手配置实体类
 */
@Data
@TableName("prompt_helper_configs")
public class PromptHelperConfig {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 主体强化
     */
    @TableField("subject_enhancement")
    private String subjectEnhancement;
    
    /**
     * 场景强化
     */
    @TableField("scene_enhancement")
    private String sceneEnhancement;
    
    /**
     * 构图与视角增强
     */
    @TableField("camera_composition")
    private String cameraComposition;
    
    /**
     * 光影与画质增强
     */
    @TableField("light_quality")
    private String lightQuality;
    
    /**
     * 细节与修饰增强
     */
    @TableField("detail_enhancement")
    private String detailEnhancement;

    /**
     * 算力消耗
     */
    @TableField("compute_consumption")
    private Integer computeConsumption;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
