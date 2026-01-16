package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.MenuConfig;
import com.meitou.admin.mapper.MenuConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端菜单配置服务类
 */
@Service
@RequiredArgsConstructor
public class MenuConfigService extends ServiceImpl<MenuConfigMapper, MenuConfig> {
    
    private final MenuConfigMapper menuMapper;
    
    /**
     * 获取菜单配置（按站点）
     * 多租户插件会自动过滤，这里只需要查询即可
     * 
     * @return 菜单列表
     */
    public List<MenuConfig> getMenus() {
        LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(MenuConfig::getId);
        return menuMapper.selectList(wrapper);
    }
    
    /**
     * 获取指定站点的菜单配置（管理后台使用）
     * 注意：调用此方法前，需要先设置 SiteContext.setSiteId(siteId)，
     * 这样多租户插件会自动添加 site_id 过滤条件
     * 
     * @param siteId 站点ID
     * @return 菜单列表
     */
    public List<MenuConfig> getMenusBySiteId(Long siteId) {
        // 不在这里添加 siteId 条件，因为多租户插件会自动添加
        // 如果在这里添加，会导致 SQL 中出现重复的 site_id 条件
        LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(MenuConfig::getId);
        return menuMapper.selectList(wrapper);
    }
    
    /**
     * 更新菜单配置
     * 
     * @param id 菜单ID
     * @param menu 菜单信息
     * @return 更新后的菜单
     */
    public MenuConfig updateMenu(Long id, MenuConfig menu) {
        MenuConfig existing = getMenuById(id);
        
        if (menu.getLabel() != null) {
            existing.setLabel(menu.getLabel());
        }
        if (menu.getIsVisible() != null) {
            existing.setIsVisible(menu.getIsVisible());
        }
        if (menu.getCode() != null) {
            existing.setCode(menu.getCode());
        }
        
        menuMapper.updateById(existing);
        return existing;
    }
    
    /**
     * 获取用户端有效的菜单列表（根据可见性过滤）
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 有效的菜单列表
     */
    public List<MenuConfig> getVisibleMenus() {
        LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuConfig::getIsVisible, true); // 只返回可见的菜单
        wrapper.orderByAsc(MenuConfig::getId);
        return menuMapper.selectList(wrapper);
    }
    
    /**
     * 根据ID获取菜单
     * 注意：此方法会经过多租户过滤
     * 
     * @param id 菜单ID
     * @return 菜单
     */
    public MenuConfig getMenuById(Long id) {
        MenuConfig menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new RuntimeException("菜单不存在");
        }
        return menu;
    }
    
    /**
     * 根据ID获取菜单（管理后台使用，不经过多租户过滤）
     * 
     * @param id 菜单ID
     * @return 菜单
     */
    public MenuConfig getMenuByIdWithoutTenant(Long id) {
        // 使用原生SQL查询，绕过多租户插件
        // 注意：这里需要手动添加 deleted = 0 的条件
        LambdaQueryWrapper<MenuConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuConfig::getId, id);
        wrapper.eq(MenuConfig::getDeleted, 0);
        MenuConfig menu = menuMapper.selectOne(wrapper);
        if (menu == null) {
            throw new RuntimeException("菜单不存在");
        }
        return menu;
    }
}

