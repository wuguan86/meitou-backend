package com.meitou.admin.dto.app;

import lombok.Data;

/**
 * 上传资产请求DTO
 */
@Data
public class UploadAssetRequest {
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 类型：image-图片，video-视频，audio-音频
     */
    private String type;
    
    /**
     * 分类：medical-医美类，ecommerce-电商类，life-生活服务类
     */
    private String category;
    
    /**
     * 文件URL（上传后由后端返回）
     */
    private String url;
    
    /**
     * 缩略图URL（可选）
     */
    private String thumbnail;
    
    /**
     * 文件夹路径（可选）
     */
    private String folder;
}
