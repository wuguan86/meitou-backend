package com.meitou.admin.service;

import com.meitou.admin.entity.Site;
import com.meitou.admin.mapper.SiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点缓存服务
 * 在系统启动时加载所有站点信息到内存缓存，提供快速查询
 */
@Service
@RequiredArgsConstructor
public class SiteCacheService {
    
    private final SiteMapper siteMapper;
    
    /**
     * 站点缓存：按ID存储
     */
    private final Map<Long, Site> siteCacheById = new ConcurrentHashMap<>();
    
    /**
     * 站点缓存：按域名存储
     */
    private final Map<String, Site> siteCacheByDomain = new ConcurrentHashMap<>();
    
    /**
     * 站点缓存：按代码存储
     */
    private final Map<String, Site> siteCacheByCode = new ConcurrentHashMap<>();
    
    /**
     * 初始化站点缓存
     * 从数据库加载所有启用的站点到内存
     */
    public void initCache() {
        // 清空现有缓存
        siteCacheById.clear();
        siteCacheByDomain.clear();
        siteCacheByCode.clear();
        
        // 从数据库查询所有未删除的站点
        List<Site> sites = siteMapper.selectList(null);
        
        // 加载到缓存
        for (Site site : sites) {
            if (site.getDeleted() == null || site.getDeleted() == 0) {
                siteCacheById.put(site.getId(), site);
                if (StringUtils.hasText(site.getDomain())) {
                    siteCacheByDomain.put(site.getDomain(), site);
                }
                if (StringUtils.hasText(site.getCode())) {
                    siteCacheByCode.put(site.getCode(), site);
                }
            }
        }
    }
    
    /**
     * 根据站点ID获取站点信息
     * 
     * @param siteId 站点ID
     * @return 站点信息，如果不存在则返回null
     */
    public Site getSiteById(Long siteId) {
        if (siteId == null) {
            return null;
        }
        return siteCacheById.get(siteId);
    }
    
    /**
     * 根据域名获取站点信息
     * 
     * @param domain 域名
     * @return 站点信息，如果不存在则返回null
     */
    public Site getSiteByDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        return siteCacheByDomain.get(domain);
    }
    
    /**
     * 根据站点代码获取站点信息
     * 
     * @param code 站点代码
     * @return 站点信息，如果不存在则返回null
     */
    public Site getSiteByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return siteCacheByCode.get(code);
    }
    
    /**
     * 刷新缓存
     * 当站点信息变更时调用，重新加载所有站点
     */
    public void refreshCache() {
        initCache();
    }
    
    /**
     * 获取所有站点
     * 
     * @return 所有站点列表
     */
    public List<Site> getAllSites() {
        return siteCacheById.values().stream().toList();
    }
}

