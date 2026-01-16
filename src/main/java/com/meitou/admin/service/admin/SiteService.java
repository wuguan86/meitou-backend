package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.Site;
import com.meitou.admin.mapper.SiteMapper;
import com.meitou.admin.service.SiteCacheService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端站点服务类
 * 处理站点的增删改查和缓存刷新
 */
@Service
@RequiredArgsConstructor
public class SiteService extends ServiceImpl<SiteMapper, Site> {
    
    private final SiteMapper siteMapper;
    private final SiteCacheService siteCacheService;
    private final FileStorageService fileStorageService;
    
    /**
     * 获取所有站点列表
     * 
     * @return 站点列表
     */
    public List<Site> getAllSites() {
        LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Site::getDeleted, 0);
        wrapper.orderByAsc(Site::getId);
        List<Site> sites = siteMapper.selectList(wrapper);
        // 处理签名URL
        sites.forEach(site -> {
            if (site.getLogo() != null) {
                site.setLogo(fileStorageService.getFileUrl(site.getLogo()));
            }
            if (site.getFavicon() != null) {
                site.setFavicon(fileStorageService.getFileUrl(site.getFavicon()));
            }
        });
        return sites;
    }
    
    /**
     * 根据ID获取站点
     * 
     * @param id 站点ID
     * @return 站点信息
     */
    public Site getSiteById(Long id) {
        Site site = siteMapper.selectById(id);
        if (site == null) {
            throw new RuntimeException("站点不存在");
        }
        // 处理签名URL
        if (site.getLogo() != null) {
            site.setLogo(fileStorageService.getFileUrl(site.getLogo()));
        }
        if (site.getFavicon() != null) {
            site.setFavicon(fileStorageService.getFileUrl(site.getFavicon()));
        }
        return site;
    }
    
    /**
     * 创建站点
     * 
     * @param site 站点信息
     * @return 创建的站点
     */
    @Transactional
    public Site createSite(Site site) {
        // 检查域名是否已存在
        LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Site::getDomain, site.getDomain());
        wrapper.eq(Site::getDeleted, 0);
        Site existing = siteMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("域名已存在");
        }
        
        // 检查代码是否已存在
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Site::getCode, site.getCode());
        wrapper.eq(Site::getDeleted, 0);
        existing = siteMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("站点代码已存在");
        }
        
        // 设置默认值
        if (site.getStatus() == null) {
            site.setStatus("active");
        }
        
        siteMapper.insert(site);
        
        // 刷新缓存
        siteCacheService.refreshCache();
        
        return site;
    }
    
    /**
     * 更新站点信息
     *
     * @param id 站点ID
     * @param site 站点信息
     * @return 更新后的站点
     */
    @Transactional
    public Site updateSite(Long id, Site site) {
        Site existing = getSiteById(id);
        
        // 更新域名
        if (site.getDomain() != null && !site.getDomain().isEmpty() && !site.getDomain().equals(existing.getDomain())) {
            // 检查域名是否已存在
            LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Site::getDomain, site.getDomain());
            wrapper.ne(Site::getId, id);
            wrapper.eq(Site::getDeleted, 0);
            if (siteMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("域名已存在");
            }
            existing.setDomain(site.getDomain());
        }
        
        // 更新使用手册
        if (site.getManual() != null) {
            existing.setManual(site.getManual());
        }
        
        // 更新用户协议
        if (site.getUserAgreement() != null) {
            existing.setUserAgreement(site.getUserAgreement());
        }
        
        // 更新隐私政策
        if (site.getPrivacyPolicy() != null) {
            existing.setPrivacyPolicy(site.getPrivacyPolicy());
        }

        // 更新版权信息
        if (site.getCopyright() != null) {
            existing.setCopyright(site.getCopyright());
        }
        
        // 更新描述
        if (site.getDescription() != null) {
            existing.setDescription(site.getDescription());
        }

        // 更新底部版权信息
        if (site.getFooterCopyright() != null) {
            existing.setFooterCopyright(site.getFooterCopyright());
        }

        // 更新Logo
        if (site.getLogo() != null) {
            existing.setLogo(site.getLogo());
        }

        // 更新网站名称
        if (site.getWebsiteName() != null) {
            existing.setWebsiteName(site.getWebsiteName());
        }

        // 更新登录框Slogan
        if (site.getLoginSubtext() != null) {
            existing.setLoginSubtext(site.getLoginSubtext());
        }

        // 更新网站Title
        if (site.getWebsiteTitle() != null) {
            existing.setWebsiteTitle(site.getWebsiteTitle());
        }

        // 更新Favicon
        if (site.getFavicon() != null) {
            existing.setFavicon(site.getFavicon());
        }

        siteMapper.updateById(existing);
        siteCacheService.refreshCache();
        return existing;
    }

    /**
     * 更新站点域名（只能修改域名）
     * 
     * @param id 站点ID
     * @param domain 新域名
     * @return 更新后的站点
     */
    @Transactional
    public Site updateDomain(Long id, String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new RuntimeException("域名不能为空");
        }
        
        Site existing = getSiteById(id);
        
        // 如果域名没有改变，直接返回
        if (domain.equals(existing.getDomain())) {
            return existing;
        }
        
        // 检查新域名是否已被其他站点使用
        LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Site::getDomain, domain.trim());
        wrapper.ne(Site::getId, id);
        wrapper.eq(Site::getDeleted, 0);
        Site duplicate = siteMapper.selectOne(wrapper);
        if (duplicate != null) {
            throw new RuntimeException("域名已被其他站点使用");
        }
        
        // 更新域名
        existing.setDomain(domain.trim());
        siteMapper.updateById(existing);
        
        // 刷新缓存
        siteCacheService.refreshCache();
        
        return existing;
    }
    
//    /**
//     * 更新站点（保留用于其他场景，站点管理页面不使用）
//     *
//     * @param id 站点ID
//     * @param site 站点信息
//     * @return 更新后的站点
//     */
//    @Transactional
//    public Site updateSite(Long id, Site site) {
//        Site existing = getSiteById(id);
//
//        // 如果域名改变，检查新域名是否已存在
//        if (site.getDomain() != null && !site.getDomain().equals(existing.getDomain())) {
//            LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
//            wrapper.eq(Site::getDomain, site.getDomain());
//            wrapper.ne(Site::getId, id);
//            wrapper.eq(Site::getDeleted, 0);
//            Site duplicate = siteMapper.selectOne(wrapper);
//            if (duplicate != null) {
//                throw new RuntimeException("域名已被其他站点使用");
//            }
//            existing.setDomain(site.getDomain());
//        }
//
//        // 如果代码改变，检查新代码是否已存在
//        if (site.getCode() != null && !site.getCode().equals(existing.getCode())) {
//            LambdaQueryWrapper<Site> wrapper = new LambdaQueryWrapper<>();
//            wrapper.eq(Site::getCode, site.getCode());
//            wrapper.ne(Site::getId, id);
//            wrapper.eq(Site::getDeleted, 0);
//            Site duplicate = siteMapper.selectOne(wrapper);
//            if (duplicate != null) {
//                throw new RuntimeException("站点代码已被其他站点使用");
//            }
//            existing.setCode(site.getCode());
//        }
//
//        // 更新其他字段
//        if (site.getName() != null) {
//            existing.setName(site.getName());
//        }
//        if (site.getStatus() != null) {
//            existing.setStatus(site.getStatus());
//        }
//        if (site.getDescription() != null) {
//            existing.setDescription(site.getDescription());
//        }
//
//        siteMapper.updateById(existing);
//
//        // 刷新缓存
//        siteCacheService.refreshCache();
//
//        return existing;
//    }
    
    /**
     * 删除站点（逻辑删除）
     * 
     * @param id 站点ID
     */
    @Transactional
    public void deleteSite(Long id) {
        Site site = getSiteById(id);
        site.setDeleted(1);
        siteMapper.updateById(site);
        
        // 刷新缓存
        siteCacheService.refreshCache();
    }
    
    /**
     * 启用/禁用站点
     * 
     * @param id 站点ID
     * @param status 状态：active-启用，disabled-禁用
     * @return 更新后的站点
     */
    @Transactional
    public Site updateStatus(Long id, String status) {
        Site site = getSiteById(id);
        site.setStatus(status);
        siteMapper.updateById(site);
        
        // 刷新缓存
        siteCacheService.refreshCache();
        
        return site;
    }
    
    /**
     * 刷新站点缓存
     */
    public void refreshCache() {
        siteCacheService.refreshCache();
    }
}

