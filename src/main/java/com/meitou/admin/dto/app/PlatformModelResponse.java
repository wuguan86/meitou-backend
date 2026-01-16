package com.meitou.admin.dto.app;

import lombok.Data;
import java.util.List;

/**
 * 平台模型响应DTO
 * 用于返回平台的模型列表
 */
@Data
public class PlatformModelResponse {
    /**
     * 平台ID
     */
    private Long platformId;
    
    /**
     * 平台名称
     */
    private String platformName;
    
    /**
     * 支持的模型列表（已解析为数组）
     */
    private List<ModelInfo> models;
    
    /**
     * 模型信息
     */
    @Data
    public static class ModelInfo {
        /**
         * 模型ID/代码
         */
        private String id;
        
        /**
         * 模型显示名称
         */
        private String name;

        /**
         * 支持的分辨率列表
         */
        private List<String> resolutions;

        /**
         * 支持的宽高比列表
         */
        private List<String> ratios;

        /**
         * 支持的时长列表（视频模型）
         */
        private List<Integer> durations;

        /**
         * 支持的数量列表（图片模型）
         */
        private List<Integer> quantities;

        /**
         * 默认消耗积分
         */
        private Integer defaultCost;
    }
}

