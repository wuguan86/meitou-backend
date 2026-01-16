package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.VoiceCloneRequest;
import com.meitou.admin.dto.app.VoiceCloneResponse;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.service.app.VoiceCloneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端声音克隆控制器
 * 处理声音克隆接口
 */
@RestController
@RequestMapping("/api/app/voice")
@RequiredArgsConstructor
public class VoiceCloneController {
    
    private final VoiceCloneService voiceCloneService;
    
    /**
     * 声音克隆接口
     * 
     * @param request 声音克隆请求
     * @param token 用户Token（从请求头获取）
     * @return 克隆响应
     */
    @PostMapping("/clone")
    public Result<VoiceCloneResponse> cloneVoice(
            @Valid @RequestBody VoiceCloneRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        try {
            // 从token获取用户ID
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error(401, "未登录或token无效");
            }
            
            // 调用服务克隆声音
            VoiceCloneResponse response = voiceCloneService.cloneVoice(request, userId);
            return Result.success("克隆成功", response);
            
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error("克隆失败：" + e.getMessage());
        }
    }
    
    /**
     * 从token中解析用户ID
     * token格式：app_token_{userId}_{timestamp} 或 Bearer app_token_{userId}_{timestamp}
     * 
     * @param token 用户Token
     * @return 用户ID，如果token无效则返回null
     */
    private Long getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        // 移除Bearer前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        // 解析token格式：app_token_{userId}_{timestamp}
        if (token.startsWith("app_token_")) {
            String[] parts = token.substring(10).split("_");
            if (parts.length > 0) {
                try {
                    return Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    // token格式错误
                    return null;
                }
            }
        }
        
        return null;
    }
}

