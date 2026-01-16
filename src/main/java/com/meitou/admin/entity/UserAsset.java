package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户资产实体类
 * 对应数据库表：user_assets
 */
@Data
@TableName("user_assets")
public class UserAsset {
    
    /**
     * 资产ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 类型：image-图片，video-视频，audio-音频
     */
    private String type;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 文件夹路径（用于组织资产，如 "images/2024/01" 或 "videos/promos"）
     */
    private String folder;
    
    /**
     * 文件URL
     */
    private String url;
    
    /**
     * 缩略图URL
     */
    private String thumbnail;
    
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
     * 状态：published-展示中，hidden-已下架
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
     * 上传时间
     */
    @TableField("upload_date")
    private LocalDateTime uploadDate;
    
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

