package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.MenuConfig;
import com.meitou.admin.service.admin.MenuConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端菜单管理控制器
 */
@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class MenuController {
    
    private final MenuConfigService menuService;
    
    /**
     * 获取菜单配置（按站点）
     * 
     * @param siteId 站点ID（可选，如果不提供则返回所有站点的菜单）
     * @return 菜单列表
     */
    @GetMapping
    @SiteScope(required = false) // 使用 AOP 自动处理 SiteContext，siteId 不是必填
    public Result<List<MenuConfig>> getMenus(@RequestParam(required = false) Long siteId) {
        List<MenuConfig> menus;
        if (siteId != null) {
            // SiteContext 已由 @SiteScope 注解自动设置
            menus = menuService.getMenusBySiteId(siteId);
        } else {
            // 如果没有指定 siteId，清除 SiteContext，让多租户插件不添加过滤条件
            // 但 menu_configs 表需要多租户过滤，所以这里应该返回空列表或所有数据
            // 实际上，管理后台应该总是指定 siteId
            menus = menuService.getMenus();
        }
        return Result.success(menus);
    }
    
    /**
     * 更新菜单配置
     * 
     * @param id 菜单ID
     * @param siteId 站点ID（必传）
     * @param menu 菜单信息
     * @return 更新后的菜单
     */
    @PutMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext，siteId 是必填参数（默认）
    public Result<MenuConfig> updateMenu(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestBody MenuConfig menu) {
        // SiteContext 已由 @SiteScope 注解自动设置
        // 执行更新
        MenuConfig updated = menuService.updateMenu(id, menu);
        return Result.success("更新成功", updated);
    }
}

