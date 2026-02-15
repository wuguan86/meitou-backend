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
@TableName("user_point_buckets")
public class UserPointBucket {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("source_type")
    private String sourceType;

    @TableField("source_ref_id")
    private String sourceRefId;

    @TableField("total_points")
    private Integer totalPoints;

    @TableField("remaining_points")
    private Integer remainingPoints;

    @TableField("granted_at")
    private LocalDateTime grantedAt;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    @TableField("site_id")
    private Long siteId;
}

