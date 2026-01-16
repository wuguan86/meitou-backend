package com.meitou.admin.dto.app;

import lombok.Data;

/**
 * 声音克隆响应 DTO
 */
@Data
public class VoiceCloneResponse {
    
    /**
     * 生成的音频URL
     */
    private String audioUrl;
    
    /**
     * 任务ID（如果API是异步的）
     */
    private String taskId;
    
    /**
     * 生成状态：success-成功，processing-处理中，failed-失败
     */
    private String status;
    
    /**
     * 错误消息（如果失败）
     */
    private String errorMessage;
}

