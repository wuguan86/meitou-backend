package com.meitou.admin.dto;

import lombok.Data;
import java.util.List;

/**
 * API平台响应DTO（包含接口列表）
 */
@Data
public class ApiPlatformResponse {
    /**
     * 平台ID
     */
    private Long id;
    
    /**
     * 平台名称
     */
    private String name;
    
    /**
     * 别名
     */
    private String alias;
    
    /**
     * 站点ID（多租户字段，NULL表示所有站点通用）
     */
    private Long siteId;
    
    /**
     * 节点信息
     */
    private String nodeInfo;
    
    /**
     * 是否启用
     */
    private Boolean isEnabled;
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 支持的模型列表（JSON字符串）
     */
    private String supportedModels;
    
    /**
     * API类型：image_analysis-图片分析，video_analysis-视频分析，txt2img-文生图，img2img-图生图，txt2video-文生视频，img2video-图生视频，voice_clone-声音克隆
     */
    private String type;
    
    /**
     * 接口列表
     */
    private List<ApiInterfaceResponse> interfaces;
}

