package com.meitou.admin.controller.admin;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.Site;
import com.meitou.admin.service.admin.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端站点管理控制器
 * 处理站点的增删改查
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/sites")
@RequiredArgsConstructor
public class SiteController {
    
    private final SiteService siteService;
    
    /**
     * 获取所有站点列表
     * 
     * @return 站点列表
     */
    @GetMapping
    public Result<List<Site>> getAllSites() {
        List<Site> sites = siteService.getAllSites();
        return Result.success("查询成功", sites);
    }
    
    /**
     * 根据ID获取站点
     * 
     * @param id 站点ID
     * @return 站点信息
     */
    @GetMapping("/{id}")
    public Result<Site> getSiteById(@PathVariable Long id) {
        Site site = siteService.getSiteById(id);
        return Result.success("查询成功", site);
    }
    
    /**
     * 创建站点
     * 
     * @param site 站点信息
     * @return 创建的站点
     */
    @PostMapping
    public Result<Site> createSite(@Valid @RequestBody Site site) {
        Site created = siteService.createSite(site);
        return Result.success("创建成功", created);
    }
    
    /**
     * 更新站点信息
     * 
     * @param id 站点ID
     * @param site 站点信息
     * @return 更新后的站点
     */
    @PutMapping("/{id}")
    public Result<Site> updateSite(@PathVariable Long id, @RequestBody Site site) {
        Site updated = siteService.updateSite(id, site);
        return Result.success("更新成功", updated);
    }

    /**
     * 更新站点域名（只能修改域名）
     * 
     * @param id 站点ID
     * @param domain 新域名
     * @return 更新后的站点
     */
    @PutMapping("/{id}/domain")
    public Result<Site> updateDomain(@PathVariable Long id, @RequestParam String domain) {
        Site updated = siteService.updateDomain(id, domain);
        return Result.success("域名更新成功", updated);
    }
    
    /**
     * 删除站点（逻辑删除）
     * 
     * @param id 站点ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteSite(@PathVariable Long id) {
        siteService.deleteSite(id);
        return Result.success("删除成功");
    }
    
    /**
     * 启用/禁用站点
     * 
     * @param id 站点ID
     * @param status 状态：active-启用，disabled-禁用
     * @return 更新后的站点
     */
    @PutMapping("/{id}/status")
    public Result<Site> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Site site = siteService.updateStatus(id, status);
        return Result.success("更新成功", site);
    }
    
    /**
     * 刷新站点缓存
     * 
     * @return 操作结果
     */
    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        siteService.refreshCache();
        return Result.success("缓存刷新成功");
    }
}

