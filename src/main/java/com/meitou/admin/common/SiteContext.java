package com.meitou.admin.common;

/**
 * 站点上下文工具类
 * 使用ThreadLocal存储当前请求的站点ID，供多租户插件使用
 */
public class SiteContext {
    
    /**
     * ThreadLocal存储站点ID
     */
    private static final ThreadLocal<Long> SITE_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * 设置当前请求的站点ID
     * 
     * @param siteId 站点ID
     */
    public static void setSiteId(Long siteId) {
        SITE_ID_HOLDER.set(siteId);
    }
    
    /**
     * 获取当前请求的站点ID
     * 
     * @return 站点ID，如果未设置则返回null
     */
    public static Long getSiteId() {
        return SITE_ID_HOLDER.get();
    }
    
    /**
     * 清除当前请求的站点ID
     * 在请求结束后调用，避免内存泄漏
     */
    public static void clear() {
        SITE_ID_HOLDER.remove();
    }
}

