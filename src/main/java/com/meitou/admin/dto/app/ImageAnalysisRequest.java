package com.meitou.admin.dto.app;

import lombok.Data;

@Data
public class ImageAnalysisRequest {
    /**
     * 图片地址
     */
    private String image;
    
    /**
     * 分析方向/提示词
     */
    private String direction;
    
    /**
     * 模型ID
     */
    private String model;
}
