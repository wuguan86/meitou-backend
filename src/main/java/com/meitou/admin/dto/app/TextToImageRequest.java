package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

/**
 * 文生图请求 DTO
 */
@Data
public class TextToImageRequest {
    
    /**
     * 提示词（必填）
     */
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    
    /**
     * 模型名称（可选）
     */
    private String model;
    
    /**
     * 图片宽高比（可选，如：16:9, 9:16, 1:1等）
     */
    private String aspectRatio;
    
    /**
     * 分辨率（可选，如：1K, 2K, 4K）
     */
    private String resolution;
    
    /**
     * 生成数量（可选，默认1，范围1-4）
     */
    @Min(value = 1, message = "生成数量最少为1")
    @Max(value = 4, message = "生成数量最多为4")
    private Integer quantity = 1;

    /**
     * 回调地址（可选，填"-1"表示直接返回任务ID）
     */
    private String webHook;

    /**
     * 是否关闭进度回复（可选，建议搭配webHook使用）
     */
    private Boolean shutProgress;
}
