package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.MenuConfig;
import com.meitou.admin.service.admin.MenuConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端菜单控制器
 * 提供菜单展示相关的接口
 */
@RestController("appMenuController")
@RequestMapping("/api/app/menus")
@RequiredArgsConstructor
public class MenuAppController {
    
    private final MenuConfigService menuService;
    
    /**
     * 获取有效的菜单列表（用户端）
     * 只返回可见的菜单项
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 可见的菜单列表
     */
    @GetMapping
    public Result<List<MenuConfig>> getVisibleMenus() {
        List<MenuConfig> menus = menuService.getVisibleMenus();
        return Result.success(menus);
    }
}

