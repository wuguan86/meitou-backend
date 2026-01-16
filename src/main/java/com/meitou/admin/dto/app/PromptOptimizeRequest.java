package com.meitou.admin.dto.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 提示词优化请求
 */
@Data
public class PromptOptimizeRequest {
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * 是否流式返回
     */
    private Boolean stream;
    
    /**
     * 消息列表
     */
    private List<Map<String, Object>> messages;

    /**
     * 获取提示词内容 (从messages中提取)
     */
    @JsonIgnore
    public String getPrompt() {
        if (messages != null && !messages.isEmpty()) {
            // 倒序查找最后一条用户消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                Map<String, Object> msg = messages.get(i);
                if (msg != null && "user".equals(msg.get("role"))) {
                    Object content = msg.get("content");
                    return content != null ? content.toString() : "";
                }
            }
        }
        return "";
    }
}
