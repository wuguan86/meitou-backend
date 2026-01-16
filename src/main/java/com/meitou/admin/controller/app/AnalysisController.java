package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.AnalysisResponse;
import com.meitou.admin.dto.app.ImageAnalysisRequest;
import com.meitou.admin.dto.app.VideoAnalysisRequest;
import com.meitou.admin.service.app.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端图视分析控制器
 * 处理图片分析和视频分析接口
 */
@RestController
@RequestMapping("/api/app/analysis")
@RequiredArgsConstructor
public class AnalysisController {
    
    private final AnalysisService analysisService;
    
    /**
     * 图片分析接口
     * 
     * @param request 图片分析请求
     * @param token 用户Token（从请求头获取）
     * @return 分析响应
     */
    @PostMapping("/image")
    public Result<AnalysisResponse> analyzeImage(
            @Valid @RequestBody ImageAnalysisRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        try {
            // 从token获取用户ID
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error(401, "未登录或token无效");
            }
            
            // 调用服务分析图片
            AnalysisResponse response = analysisService.analyzeImage(request, userId);
            return Result.success("分析成功", response);
            
        } catch (Exception e) {
            return Result.error("分析失败：" + e.getMessage());
        }
    }
    
    /**
     * 视频分析接口
     * 
     * @param request 视频分析请求
     * @param token 用户Token（从请求头获取）
     * @return 分析响应
     */
    @PostMapping("/video")
    public Result<AnalysisResponse> analyzeVideo(
            @Valid @RequestBody VideoAnalysisRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        try {
            // 从token获取用户ID
            Long userId = getUserIdFromToken(token);
            if (userId == null) {
                return Result.error(401, "未登录或token无效");
            }
            
            // 调用服务分析视频
            AnalysisResponse response = analysisService.analyzeVideo(request, userId);
            return Result.success("分析成功", response);
            
        } catch (Exception e) {
            return Result.error("分析失败：" + e.getMessage());
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

