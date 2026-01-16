package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.MarketingAd;
import com.meitou.admin.service.admin.MarketingAdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端营销管理控制器
 */
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
public class MarketingController {
    
    private final MarketingAdService adService;
    
    /**
     * 获取广告列表
     * 根据站点ID（医美类、电商类、生活服务类）筛选广告
     * 
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @return 广告列表
     */
    @GetMapping("/ads")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<List<MarketingAd>> getAds(@RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        List<MarketingAd> ads = adService.getAdsBySiteId(siteId);
        return Result.success(ads);
    }
    
    /**
     * 创建广告
     * 
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param ad 广告信息
     * @return 创建的广告
     */
    @PostMapping("/ads")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<MarketingAd> createAd(
            @RequestParam(required = true) Long siteId,
            @RequestBody MarketingAd ad) {
        // SiteContext 已由 @SiteScope 注解自动设置
        // 确保广告的siteId与请求参数一致
        ad.setSiteId(siteId);
        MarketingAd created = adService.createAd(ad);
        return Result.success("创建成功", created);
    }
    
    /**
     * 更新广告
     * 
     * @param id 广告ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @param ad 广告信息
     * @return 更新后的广告
     */
    @PutMapping("/ads/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<MarketingAd> updateAd(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestBody MarketingAd ad) {
        // SiteContext 已由 @SiteScope 注解自动设置
        // 确保广告的siteId与请求参数一致
        ad.setSiteId(siteId);
        MarketingAd updated = adService.updateAd(id, ad);
        return Result.success("更新成功", updated);
    }
    
    /**
     * 删除广告
     * 
     * @param id 广告ID
     * @param siteId 站点ID（必传）：1=医美类, 2=电商类, 3=生活服务类
     * @return 删除结果
     */
    @DeleteMapping("/ads/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<Void> deleteAd(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        adService.deleteAd(id);
        return Result.success("删除成功");
    }
}

