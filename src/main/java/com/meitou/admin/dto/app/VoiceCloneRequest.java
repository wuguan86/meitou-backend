package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 声音克隆请求 DTO
 */
@Data
public class VoiceCloneRequest {
    
    /**
     * 参考音频URL或base64（必填）
     */
    @NotBlank(message = "参考音频不能为空")
    private String audio;
    
    /**
     * 要合成的文本（必填）
     */
    @NotBlank(message = "文本不能为空")
    private String text;
    
    /**
     * 语言代码（可选，如：zh-CN, en-US）
     */
    private String language = "zh-CN";
    
    /**
     * 模型名称（可选）
     */
    private String model;
}

