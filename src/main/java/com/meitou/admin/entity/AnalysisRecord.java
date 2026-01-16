package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分析记录实体类
 * 对应数据库表：analysis_record
 */
@Data
@TableName("analysis_record")
public class AnalysisRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 类型：image-图片分析, video-视频分析, prompt-提示词优化
     */
    private String type;

    /**
     * 内容：图片URL、视频URL或提示词
     */
    private String content;

    /**
     * 分析结果或优化后的提示词
     */
    private String result;

    /**
     * 状态：0-进行中, 1-成功, 2-失败
     */
    private Integer status;

    /**
     * 错误信息
     */
    @TableField("error_msg")
    private String errorMsg;

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

    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;
}
