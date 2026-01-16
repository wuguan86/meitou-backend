package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 发布内容实体类
 * 对应数据库表：published_contents
 */
@Data
@TableName("published_contents")
public class PublishedContent {
    
    /**
     * 发布内容ID（主键，自增）
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
    @TableField("user_name")
    private String userName;
    
    /**
     * 用户头像URL
     */
    @TableField("user_avatar_url")
    private String userAvatarUrl;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 类型：image-图片，video-视频
     */
    private String type;
    
    /**
     * 生成类型：txt2img-文生图，img2img-图生图，txt2video-文生视频，img2video-图生视频
     */
    @TableField("generation_type")
    private String generationType;


    /**
     * 内容URL
     */
    @TableField("content_url")
    private String contentUrl;
    
    /**
     * 缩略图URL或者静态封面图
     */
    private String thumbnail;
    
    /**
     * 生成配置参数（JSON格式）
     */
    @TableField("generation_config")
    private String generationConfig;
    
    /**
     * 状态：published-已发布，hidden-已隐藏
     */
    private String status;
    
    /**
     * 是否置顶：0-否，1-是
     */
    @TableField("is_pinned")
    private Boolean isPinned;
    
    /**
     * 点赞数
     */
    @TableField("like_count")
    private Integer likeCount;
    
    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
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
    /**
     * 当前用户是否已点赞（非数据库字段）
     */
    @TableField(exist = false)
    private Boolean isLiked;
}
