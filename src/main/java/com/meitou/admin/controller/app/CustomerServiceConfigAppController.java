package com.meitou.admin.controller.app;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.CustomerServiceConfig;
import com.meitou.admin.service.admin.CustomerServiceConfigService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/customer-service")
@RequiredArgsConstructor
public class CustomerServiceConfigAppController {

    private final CustomerServiceConfigService configService;
    private final FileStorageService fileStorageService;

    @GetMapping("/config")
    @SiteScope
    public Result<CustomerServiceConfig> getConfig(@RequestParam(required = true) Long siteId) {
        CustomerServiceConfig config = configService.getConfigBySiteId(siteId);
        // 前端 SecureImage 组件会自动处理 URL 签名，这里不需要重复签名
        // 且 Admin 端也是返回原始 URL，保持一致
        return Result.success(config);
    }
}
