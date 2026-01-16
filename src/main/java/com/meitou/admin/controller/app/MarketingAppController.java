package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.MarketingAd;
import com.meitou.admin.service.admin.MarketingAdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端营销广告控制器
 * 提供广告展示相关的接口
 */
@RestController
@RequestMapping("/api/app/marketing")
@RequiredArgsConstructor
public class MarketingAppController {
    
    private final MarketingAdService adService;
    
    /**
     * 获取有效的广告列表（用户端）
     * 只返回当前时间在有效期内、已激活、全屏的广告，并按position排序
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 有效的广告列表
     */
    @GetMapping("/ads")
    public Result<List<MarketingAd>> getActiveAds() {
        List<MarketingAd> ads = adService.getActiveAds();
        return Result.success(ads);
    }
}

