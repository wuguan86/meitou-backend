package com.meitou.admin.dto.app;

import lombok.Data;
import java.util.List;

/**
 * 图片生成响应 DTO
 */
@Data
public class ImageGenerationResponse {
    
    /**
     * 生成的图片URL列表
     */
    private List<String> imageUrls;

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
    private Integer progress;
    
    /**
     * 视频URL（用于视频生成任务）
     */
    private String videoUrl;

    /**
     * 外部任务ID/PID (用于后续操作，如保存角色)
     */
    private String pid;
}
