package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PromptHelperConfig;
import com.meitou.admin.service.admin.PromptHelperConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端提示词助手配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/prompt-helper")
@RequiredArgsConstructor
public class PromptHelperConfigAppController {

    private final PromptHelperConfigService configService;

    /**
     * 获取当前站点的提示词助手配置
     *
     * @return 配置信息
     */
    @GetMapping("/config")
    public Result<PromptHelperConfig> getConfig() {
        // 多租户插件会自动根据 SiteContext 中的 site_id 进行过滤
        PromptHelperConfig config = configService.getConfig();
        
        // 如果没有配置，返回空对象或默认值，避免前端报错
        if (config == null) {
            config = new PromptHelperConfig();
        }
        
        return Result.success(config);
    }
}
