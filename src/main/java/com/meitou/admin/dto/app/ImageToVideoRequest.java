package com.meitou.admin.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.util.List;

/**
 * 图生视频请求 DTO
 */
@Data
public class ImageToVideoRequest {
    
    /**
     * 提示词（必填）
     */
    @NotBlank(message = "提示词不能为空")
    private String prompt;
    
    /**
     * 参考图片URL或base64（必填，对应 firstFrameUrl 或 urls 中的第一张）
     */
    @NotBlank(message = "参考图片不能为空")
    private String image;
    
    /**
     * 首帧图片URL（选填，若不传则使用 image 字段）
     */
    private String firstFrameUrl;

    /**
     * 尾帧图片URL（选填）
     */
    private String lastFrameUrl;

    /**
     * 参考图片URL列表（选填，不可与首尾帧同时使用）
     */
    private List<String> urls;

    /**
     * 回调地址（选填）
     */
    private String webHook;

    /**
     * 是否关闭进度回复（选填）
     */
    private Boolean shutProgress;

    /**
     * 模型名称（可选）
     */
    private String model;
    
    /**
     * 视频宽高比（可选，如：16:9, 9:16, 1:1等）
     */
    private String aspectRatio;
    
    /**
     * 视频时长（可选，单位：秒，默认5秒）
     */
    @Min(value = 1, message = "视频时长最少为1秒")
    @Max(value = 60, message = "视频时长最多为60秒")
    private Integer duration = 5;
    
    /**
     * 分辨率
     */
    private String resolution;

    /**
     * remix目标id.续写视频id
     */
    private String remixTargetId;

    /**
     *上传角色视频，支持多角色(不可上传真人视频)
     */
    private List<Object> characters;
}

