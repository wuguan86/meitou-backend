package com.meitou.admin.config;

import com.meitou.admin.interceptor.SiteInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final SiteInterceptor siteInterceptor;
    
    /**
     * 注册拦截器
     * 站点拦截器用于识别请求的站点信息
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(siteInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/api/admin/**",  // 管理后台接口不需要站点拦截
                    "/error",         // 错误页面
                    "/favicon.ico"    // 图标
                );
    }
}

