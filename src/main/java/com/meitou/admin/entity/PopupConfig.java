package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 营销弹窗配置实体类
 * 对应数据库表：popup_configs
 */
@Data
@TableName("popup_configs")
public class PopupConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;

    /**
     * 弹窗名称
     */
    private String name;

    /**
     * 弹窗图片URL
     */
    @TableField("image_url")
    private String imageUrl;

    @TableField("start_date")
    private LocalDate startDate;

    @TableField("end_date")
    private LocalDate endDate;

    /**
     * 是否启用：0-否，1-是
     */
    @TableField("is_enabled")
    private Boolean isEnabled;

    /**
     * 跳转类型：external-外部网页，rich_text-富文本详情
     */
    @TableField("jump_type")
    private String jumpType;

    /**
     * 跳转链接
     */
    @TableField("jump_link")
    private String jumpLink;

    /**
     * 富文本内容
     */
    @TableField("rich_text_content")
    private String richTextContent;

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
