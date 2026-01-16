package com.meitou.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 资产文件夹实体类
 * 对应数据库表：asset_folders
 */
@Data
@TableName("asset_folders")
public class AssetFolder {
    
    /**
     * 文件夹ID（主键，自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文件夹名称
     */
    private String name;
    
    /**
     * 文件夹路径（如 "images/2024/01" 或 "videos/promos"）
     */
    private String folderPath;
    
    /**
     * 父文件夹路径（如果为null或空字符串，则表示根目录下的文件夹）
     */
    private String parentPath;
    
    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 站点ID
     */
    @TableField("site_id")
    private Long siteId;
    
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
     * 文件夹缩略图（非数据库字段）
     * 取文件夹内最新的一张图片的缩略图或URL
     */
    @TableField(exist = false)
    private String thumbnail;
}
