package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * API平台实体类
 * 对应数据库表：api_platforms
 */
@Data
@TableName("api_platforms")
public class ApiPlatform {
    
    /**
     * 平台ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 平台名称
     */
    private String name;
    
    /**
     * 别名
     */
    private String alias;
    
    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;
    
    /**
     * 是否启用：0-否，1-是
     */
    @TableField("is_enabled")
    private Boolean isEnabled;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 站点ID（多租户字段，NULL表示所有站点通用）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 节点信息：overseas-海外节点，domestic-国内直连，host-Host+接口
     */
    @TableField("node_info")
    private String nodeInfo;
    
    /**
     * 图标
     */
    private String icon;
    
    /**
     * 支持的模型列表（以#号分割的字符串，例如：flux-1.0#flux-2.0）
     */
    @TableField("supported_models")
    private String supportedModels;
    
    /**
     * API类型：image_analysis-图片分析，video_analysis-视频分析，txt2img-文生图，img2img-图生图，txt2video-文生视频，img2video-图生视频，voice_clone-声音克隆，prompt_optimize-提示词优化
     */
    private String type;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;
}

