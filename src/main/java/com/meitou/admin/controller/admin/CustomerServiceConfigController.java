package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.CustomerServiceConfig;
import com.meitou.admin.service.admin.CustomerServiceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customer-service")
@RequiredArgsConstructor
public class CustomerServiceConfigController {

    private final CustomerServiceConfigService configService;

    @GetMapping("/config")
    @SiteScope
    public Result<CustomerServiceConfig> getConfig(@RequestParam(required = true) Long siteId) {
        return Result.success(configService.getConfigBySiteId(siteId));
    }

    @PostMapping("/config")
    @SiteScope
    public Result<CustomerServiceConfig> saveConfig(
            @RequestParam(required = true) Long siteId,
            @RequestBody CustomerServiceConfig config) {
        config.setSiteId(siteId);
        return Result.success(configService.saveOrUpdateConfig(config));
    }
}
