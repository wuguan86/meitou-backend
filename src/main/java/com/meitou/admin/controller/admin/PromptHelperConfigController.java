package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PromptHelperConfig;
import com.meitou.admin.service.admin.PromptHelperConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 提示词助手配置控制器
 */
@RestController
@RequestMapping("/api/admin/prompt-helper")
@RequiredArgsConstructor
public class PromptHelperConfigController {
    
    private final PromptHelperConfigService configService;
    
    /**
     * 获取配置
     * 
     * @param siteId 站点ID（必须提供，由@SiteScope处理上下文）
     * @return 配置信息
     */
    @GetMapping
    @SiteScope(required = true)
    public Result<PromptHelperConfig> getConfig(@RequestParam Long siteId) {
        PromptHelperConfig config = configService.getConfig();
        // 如果没有配置，返回一个空对象给前端（方便前端直接编辑）
        if (config == null) {
            config = new PromptHelperConfig();
            config.setSiteId(siteId);
        }
        return Result.success(config);
    }
    
    /**
     * 保存配置
     * 
     * @param siteId 站点ID（必须提供，由@SiteScope处理上下文）
     * @param config 配置信息
     * @return 保存后的配置
     */
    @PostMapping
    @SiteScope(required = true)
    public Result<PromptHelperConfig> saveConfig(
            @RequestParam Long siteId, 
            @RequestBody PromptHelperConfig config) {
        
        // 确保 siteId 正确
        config.setSiteId(siteId);
        
        PromptHelperConfig saved = configService.saveConfig(config);
        return Result.success(saved);
    }
}
