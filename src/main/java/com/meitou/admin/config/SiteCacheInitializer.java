package com.meitou.admin.config;

import com.meitou.admin.service.SiteCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 站点缓存初始化器
 * 在Spring容器启动完成后，初始化站点缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteCacheInitializer implements ApplicationListener<ContextRefreshedEvent> {
    
    private final SiteCacheService siteCacheService;
    
    /**
     * 当Spring容器刷新完成后，初始化站点缓存
     * 
     * @param event 上下文刷新事件
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 避免重复初始化（Spring Boot可能会触发多次）
        if (event.getApplicationContext().getParent() == null) {
            log.info("开始初始化站点缓存...");
            try {
                siteCacheService.initCache();
                log.info("站点缓存初始化完成");
            } catch (Exception e) {
                log.error("站点缓存初始化失败", e);
            }
        }
    }
}

