package com.meitou.admin.dto.app;

import lombok.Data;

/**
 * 视频生成响应 DTO
 */
@Data
public class VideoGenerationResponse {
    
    /**
     * 生成的视频URL
     */
    private String videoUrl;

    private Long generationRecordId;
    
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

    /**
     * 外部内容ID/PID
     */
    private String pid;

    /**
     * 失败原因
     */
    private String failureReason;
}

