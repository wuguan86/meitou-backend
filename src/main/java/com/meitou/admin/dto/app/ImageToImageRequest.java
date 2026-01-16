package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.util.List;

/**
 * 图生图请求 DTO
 */
@Data
public class ImageToImageRequest {
    
    /**
     * 提示词（必填）
     */
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    
    /**
     * 参考图片URL列表（必填）
     */
    @NotEmpty(message = "参考图片不能为空")
    private List<String> urls;
    
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
     * 回调接口URL（选填）
     * 如果填了webHook，进度与结果则以Post请求回调地址的方式进行回复
     * 如果不使用回调，而使用轮询result接口方式获取结果，需要接口立即返回一个id，则webHook参数填"-1"
     */
    private String webHook;

    /**
     * 关闭进度回复，直接回复最终结果（选填，默认false）
     * 建议搭配webHook使用
     */
    private Boolean shutProgress;
}
