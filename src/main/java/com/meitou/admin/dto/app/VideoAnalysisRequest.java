package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 视频分析请求 DTO
 */
@Data
public class VideoAnalysisRequest {
    
    /**
     * 视频URL或base64（必填）
     */
    @NotBlank(message = "视频不能为空")
    private String video;
    
    /**
     * 分析方向（可选）
     */
    private String direction;
    
    /**
     * 模型名称（可选）
     */
    private String model;
}

