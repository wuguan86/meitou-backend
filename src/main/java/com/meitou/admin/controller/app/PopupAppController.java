package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.PopupConfig;
import com.meitou.admin.service.admin.PopupConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端弹窗控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/popup")
@RequiredArgsConstructor
public class PopupAppController {

    private final PopupConfigService popupConfigService;

    /**
     * 获取当前站点的有效弹窗配置
     * 
     * @return 弹窗配置列表
     */
    @GetMapping("/active")
    public Result<List<PopupConfig>> getActivePopups() {
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            // 如果无法获取站点ID，可能是直接访问IP或者没有配置域名，这种情况下可以返回空列表
            log.warn("无法识别当前站点ID，无法获取弹窗配置");
            return Result.success(List.of());
        }
        
        List<PopupConfig> popups = popupConfigService.getActivePopups(siteId);
        return Result.success(popups);
    }
}
