package com.meitou.admin.interceptor;

import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.Site;
import com.meitou.admin.service.SiteCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 站点拦截器
 * 从请求中识别站点信息，并存储到ThreadLocal中供多租户插件使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteInterceptor implements HandlerInterceptor {
    
    private final SiteCacheService siteCacheService;
    
    /**
     * 请求头中的站点ID字段名
     */
    private static final String HEADER_SITE_ID = "X-Site-Id";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long siteId = null;
        
        // 方式1：优先从请求头获取站点ID
        String siteIdHeader = request.getHeader(HEADER_SITE_ID);
        if (StringUtils.hasText(siteIdHeader)) {
            try {
                siteId = Long.parseLong(siteIdHeader);
                log.debug("从请求头获取站点ID: {}", siteId);
            } catch (NumberFormatException e) {
                log.warn("请求头中的站点ID格式错误: {}", siteIdHeader);
            }
        }
        
        // 方式2：如果请求头中没有，则从域名识别
        if (siteId == null) {
            // 先尝试拿原始域名
            String host = request.getHeader("X-Forwarded-Host");
            if (host == null) {
                host = request.getHeader("Host");
            }
            if (StringUtils.hasText(host)) {
                // 处理可能包含端口的情况，如：localhost:8080
                String domain = host.split(":")[0];
                Site site = siteCacheService.getSiteByDomain(domain);
                
                // If exact match fails and domain starts with www., try stripping www.
                if (site == null && domain.startsWith("www.")) {
                    String rootDomain = domain.substring(4);
                    site = siteCacheService.getSiteByDomain(rootDomain);
                    if (site != null) {
                        log.debug("Identified site ID after removing www prefix: {} -> {}", rootDomain, site.getId());
                    }
                }
                
                if (site != null) {
                    siteId = site.getId();
                    log.debug("Identified site ID from domain: {} -> {}", domain, siteId);
                } else {
                    log.debug("Site not found for domain: {}", domain);
                }
            }
        }
        
        // 将站点ID存储到ThreadLocal
        if (siteId != null) {
            SiteContext.setSiteId(siteId);
        } else {
            log.warn("无法识别站点，请求路径: {}", request.getRequestURI());
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后清除ThreadLocal，避免内存泄漏
        SiteContext.clear();
    }
}