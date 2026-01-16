package com.meitou.admin.dto.app;

import lombok.Data;

/**
 * 分析响应 DTO
 */
@Data
public class AnalysisResponse {
    
    /**
     * 分析结果文本
     */
    private String result;
    
    /**
     * 状态：success-成功，processing-处理中，failed-失败
     */
    private String status;
    
    /**
     * 错误消息（如果失败）
     */
    private String errorMessage;
}

