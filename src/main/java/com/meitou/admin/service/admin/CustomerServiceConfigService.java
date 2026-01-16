package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.CustomerServiceConfig;
import com.meitou.admin.mapper.CustomerServiceConfigMapper;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceConfigService extends ServiceImpl<CustomerServiceConfigMapper, CustomerServiceConfig> {

    public CustomerServiceConfig getConfigBySiteId(Long siteId) {
        return getOne(new LambdaQueryWrapper<CustomerServiceConfig>()
                .eq(CustomerServiceConfig::getSiteId, siteId)
                .last("LIMIT 1"));
    }

    public CustomerServiceConfig saveOrUpdateConfig(CustomerServiceConfig config) {
        CustomerServiceConfig existing = getConfigBySiteId(config.getSiteId());
        if (existing != null) {
            config.setId(existing.getId());
            updateById(config);
        } else {
            save(config);
        }
        return config;
    }
}
