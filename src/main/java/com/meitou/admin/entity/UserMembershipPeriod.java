package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_membership_periods")
public class UserMembershipPeriod {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("package_id")
    private Integer packageId;

    @TableField("level_code")
    private String levelCode;

    @TableField("billing_cycle")
    private String billingCycle;

    @TableField("start_at")
    private LocalDateTime startAt;

    @TableField("end_at")
    private LocalDateTime endAt;

    private String status;

    @TableField("order_no")
    private String orderNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @TableField("site_id")
    private Long siteId;
}

