package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 客服配置实体类
 * 对应数据库表：customer_service_config
 */
@Data
@TableName("customer_service_config")
public class CustomerServiceConfig {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;

    /**
     * 客服二维码图片URL
     */
    @TableField("qr_code_url")
    private String qrCodeUrl;

    /**
     * 联系方式文本说明
     */
    @TableField("contact_text")
    private String contactText;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
