package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员套餐配置实体类
 * 对应数据库表：membership_packages
 */
@Data
@TableName("membership_packages")
public class MembershipPackage {
    
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 套餐名称：如标准版、专业版
     */
    private String name;
    
    /**
     * 等级代码：free, standard, pro, flagship, enterprise
     */
    @TableField("level_code")
    private String levelCode;
    
    /**
     * 排序权重：越小越靠前
     */
    @TableField("sort_order")
    private Integer sortOrder;
    
    /**
     * 是否上架：0-下架, 1-上架
     */
    @TableField("is_active")
    private Boolean isActive;
    
    /**
     * 是否推荐(UI高亮边框)：0-否, 1-是
     */
    @TableField("is_recommended")
    private Boolean isRecommended;
    
    /**
     * 顶部标签文案：如首购8.5折、超值之选
     */
    @TableField("badge_text")
    private String badgeText;
    
    /**
     * 单月原价
     */
    @TableField("monthly_price")
    private BigDecimal monthlyPrice;
    
    /**
     * 单月首购/优惠价
     */
    @TableField("monthly_discount_price")
    private BigDecimal monthlyDiscountPrice;
    
    /**
     * 包年总原价
     */
    @TableField("yearly_price")
    private BigDecimal yearlyPrice;
    
    /**
     * 包年优惠后总价
     */
    @TableField("yearly_discount_price")
    private BigDecimal yearlyDiscountPrice;
    
    /**
     * 购买该套餐每月赠送的算力值
     */
    @TableField("points_reward")
    private Integer pointsReward;
    
    /**
     * 按钮文字
     */
    @TableField("button_text")
    private String buttonText;
    
    /**
     * 动作类型：buy-直接支付, contact-咨询客服
     */
    @TableField("button_type")
    private String buttonType;
    
    /**
     * 主题色：如旗舰版用紫色 #A855F7
     */
    @TableField("primary_color")
    private String primaryColor;
    
    /**
     * 权益列表JSON数组
     */
    @TableField("features_json")
    private String featuresJson;
    
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
