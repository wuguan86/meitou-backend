package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.util.List;

/**
 * 文生视频请求 DTO
 */
@Data
public class TextToVideoRequest {
    
    /**
     * 提示词（必填，只支持英文）
     */
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    
    /**
     * 模型名称（可选）
     * 支持模型: veo3.1-fast, veo3.1-pro, veo3-fast, veo3-pro
     */
    private String model;
    
    /**
     * 首帧图片URL（选填）
     * 支持模型: veo3-fast, veo3-pro, veo3.1-fast, veo3.1-pro
     */
    private String firstFrameUrl;
    
    /**
     * 尾帧图片URL（选填）
     * 需搭配firstFrameUrl使用
     * 支持模型: veo3.1-fast, veo3.1-pro
     */
    private String lastFrameUrl;
    
    /**
     * 参考图片URL列表（选填）
     * 最多支持三张图片
     * 不可搭配首尾帧使用
     * 支持尺寸: 16:9
     * 支持模型: veo3.1-fast
     */
    private List<String> urls;
    
    /**
     * 视频宽高比（可选，默认16:9）
     * 支持的比例: 16:9, 9:16
     */
    private String aspectRatio;
    
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
    
    /**
     * 视频时长（可选，单位：秒，默认10秒）
     */
    @Min(value = 1, message = "视频时长最少为1秒")
    @Max(value = 60, message = "视频时长最多为60秒")
    private Integer duration = 10;
    
    /**
     * 分辨率（可选，如：1K, 2K, 4K）
     */
    private String resolution;
}

