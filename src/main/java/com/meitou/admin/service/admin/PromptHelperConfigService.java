package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.PromptHelperConfig;
import com.meitou.admin.mapper.PromptHelperConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PromptHelperConfigService extends ServiceImpl<PromptHelperConfigMapper, PromptHelperConfig> {
    
    /**
     * 获取当前站点的配置
     * 
     * @return 配置对象，如果不存在则返回null
     */
    public PromptHelperConfig getConfig() {
        // 多租户插件会自动添加 site_id 过滤条件
        return this.getOne(new LambdaQueryWrapper<PromptHelperConfig>().last("LIMIT 1"));
    }

    /**
     * 保存或更新配置
     * 
     * @param config 配置对象
     * @return 保存后的配置
     */
    public PromptHelperConfig saveConfig(PromptHelperConfig config) {
        // 查找当前站点是否已存在配置
        PromptHelperConfig existing = getConfig();
        
        if (existing != null) {
            // 更新现有配置
            config.setId(existing.getId());
            // 保持 siteId 不变（虽然理论上应该是一样的）
            config.setSiteId(existing.getSiteId());
            updateById(config);
        } else {
            // 新增配置
            save(config);
        }
        return config;
    }
}
