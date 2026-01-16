package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色实体类
 * 对应数据库表：characters
 */
@Data
@TableName("characters")
public class Character {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 站点ID (多租户隔离)
     */
    @TableField("site_id")
    private Long siteId;

    /**
     * 所属用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色封面图/头像URL
     */
    @TableField("cover_url")
    private String coverUrl;

    /**
     * 角色演示视频URL
     */
    @TableField("video_url")
    private String videoUrl;

    /**
     * 角色模型特征数据
     */
    @TableField("model_data")
    private String modelData;

    /**
     * 来源记录ID (关联 generation_records.id)
     */
    @TableField("source_record_id")
    private Long sourceRecordId;

    /**
     * 第三方接口的任务ID/PID
     */
    @TableField("third_party_pid")
    private String thirdPartyPid;

    /**
     * 第三方角色id
     */
    @TableField("character_id")
    private String characterId;

    /**
     * 逻辑删除：0-正常，1-已删除
     */
    @TableLogic
    private Integer deleted;

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
