package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 邀请码实体类
 * 对应数据库表：invitation_codes
 */
@Data
@TableName("invitation_codes")
public class InvitationCode {
    
    /**
     * 邀请码ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 邀请码（唯一）
     */
    private String code;
    
    /**
     * 赠送积分
     */
    private Integer points;
    
    /**
     * 已使用次数
     */
    @TableField("used_count")
    private Integer usedCount;
    
    /**
     * 最大使用次数
     */
    @TableField("max_uses")
    private Integer maxUses;
    
    /**
     * 状态：active-有效，expired-已过期
     */
    private String status;

    /**
     * 类型：common-普通(积分), membership-会员
     */
    private String type;

    /**
     * 会员套餐ID（当type=membership时有效）
     */
    @TableField("package_id")
    private Integer packageId;

    /**
     * 会员时长（当type=membership时有效）
     */
    private Integer duration;

    /**
     * 会员时长单位：day, month, year（当type=membership时有效）
     */
    @TableField("duration_unit")
    private String durationUnit;
    
    /**
     * 站点ID（多租户字段）
     */
    @TableField("site_id")
    private Long siteId;
    
    /**
     * 使用渠道
     */
    private String channel;
    
    /**
     * 有效期开始时间
     */
    @TableField("valid_start_date")
    private LocalDate validStartDate;
    
    /**
     * 有效期结束时间
     */
    @TableField("valid_end_date")
    private LocalDate validEndDate;
    
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

