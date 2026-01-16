package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 站点实体类（多租户核心表）
 * 对应数据库表：sites
 */
@Data
@TableName("sites")
public class Site {
    
    /**
     * 站点ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 站点名称（如：医美类、电商类、生活服务类）
     */
    private String name;
    
    /**
     * 站点代码（如：medical、ecommerce、life）
     */
    private String code;
    
    /**
     * 域名（用于识别站点，如：medical.example.com）
     */
    private String domain;

    /**
     * Logo图片地址
     */
    private String logo;

    /**
     * 网站名称（显示在头部等位置）
     */
    private String websiteName;

    /**
     * 登录框小文字（Slogan）
     */
    private String loginSubtext;

    /**
     * 网站Title信息（浏览器标签页标题）
     */
    private String websiteTitle;

    /**
     * Favicon图标地址
     */
    private String favicon;
    
    /**
     * 状态：active-启用，disabled-禁用
     */
    private String status;
    
    /**
     * 站点描述
     */
    private String description;

    /**
     * 使用手册
     */
    private String manual;

    /**
     * 用户协议
     */
    private String userAgreement;

    /**
     * 隐私政策
     */
    private String privacyPolicy;

    /**
     * 版权信息（用于顶部显示，如版本号）
     */
    private String copyright;

    /**
     * 底部版权信息
     */
    private String footerCopyright;
    
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

