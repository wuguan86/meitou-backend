package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销广告实体类
 * 对应数据库表：marketing_ads
 */
@Data
@TableName(value = "marketing_ads", autoResultMap = true)
public class MarketingAd {
    
    /**
     * 广告ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 广告标题
     */
    private String title;
    
    /**
     * 广告图片URL
     */
    @TableField("image_url")
    private String imageUrl;
    
    /**
     * 开始时间
     */
    @TableField("start_date")
    private LocalDate startDate;
    
    /**
     * 结束时间
     */
    @TableField("end_date")
    private LocalDate endDate;
    
    /**
     * 跳转类型：external-外部网页，internal_rich-富文本详情
     */
    @TableField("link_type")
    private String linkType;
    
    /**
     * 跳转链接
     */
    @TableField("link_url")
    private String linkUrl;
    
    /**
     * 富文本内容
     */
    @TableField("rich_content")
    private String richContent;
    
    /**
     * 摘要描述
     */
    private String summary;
    
    /**
     * 关联标签（JSON格式）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    
    /**
     * 是否激活：0-否，1-是
     */
    @TableField("is_active")
    private Boolean isActive;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 广告位顺序：1-首位，2-次位，3-第三位
     */
    private Integer position;
    
    /**
     * 是否全屏：0-否，1-是
     */
    @TableField("is_full_screen")
    private Boolean isFullScreen;
    
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

