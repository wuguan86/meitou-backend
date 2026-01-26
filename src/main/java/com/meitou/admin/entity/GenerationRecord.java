package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 生成记录实体类
 * 对应数据库表：generation_records
 */
@Data
@TableName("generation_records")
public class GenerationRecord {
    
    /**
     * 记录ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 类型：image_analysis-图片分析，video_analysis-视频分析，txt2img-文生图，img2img-图生图，txt2video-文生视频，img2video-图生视频，voice_clone-声音克隆
     */
    private String type;
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 提示词
     */
    private String prompt;

    /**
     * 标题（由提示词截取第一句话生成，不存数据库）
     */
    @TableField(exist = false)
    private String title;

    /**
     * 是否发布：0-否，1-是
     */
    @TableField("is_publish")
    private String isPublish;

    /**
     * 文件类型：image-图片，video-视频
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 缩略图URL
     */
    @TableField("thumbnail_url")
    private String thumbnailUrl;

    /**
     * 生成参数（JSON格式）
     */
    @TableField("generation_params")
    private String generationParams;
    
    /**
     * 消耗积分
     */
    private Integer cost;
    
    /**
     * 状态：success-成功，failed-失败，processing-生成中
     */
    private String status;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 生成内容URL
     */
    @TableField("content_url")
    private String contentUrl;
    
    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer deleted;

    @TableField("task_id")
    private String taskId;

    /**
     * 外部内容ID/PID
     */
    private String pid;

    /**
     * 失败原因
     */
    @TableField("failure_reason")
    private String failureReason;

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
    
}

