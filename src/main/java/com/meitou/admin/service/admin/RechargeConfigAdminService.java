package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.entity.RechargeConfig;
import com.meitou.admin.mapper.RechargeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端充值配置服务类
 * 处理充值配置的增删改查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeConfigAdminService extends ServiceImpl<RechargeConfigMapper, RechargeConfig> {
    
    private final RechargeConfigMapper rechargeConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取所有充值配置
     * 管理后台需要查看所有站点的配置，所以不使用多租户过滤
     * 
     * @return 配置列表
     */
    public List<RechargeConfig> getAllConfigs() {
        LambdaQueryWrapper<RechargeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargeConfig::getDeleted, 0);
        wrapper.orderByAsc(RechargeConfig::getSiteId);
        return rechargeConfigMapper.selectList(wrapper);
    }
    
    /**
     * 根据站点ID获取充值配置
     * 注意：调用此方法前，需要先设置 SiteContext.setSiteId(siteId)，
     * 这样多租户插件会自动添加 site_id 过滤条件
     * 
     * @param siteId 站点ID
     * @return 配置
     */
    public RechargeConfig getConfigBySiteId(Long siteId) {
        // 不在这里添加 siteId 条件，因为多租户插件会自动添加
        // 如果在这里添加，会导致 SQL 中出现重复的 site_id 条件
        LambdaQueryWrapper<RechargeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RechargeConfig::getDeleted, 0);
        return rechargeConfigMapper.selectOne(wrapper);
    }
    
    /**
     * 根据ID获取充值配置
     * 
     * @param id 配置ID
     * @return 配置
     */
    public RechargeConfig getConfigById(Long id) {
        RechargeConfig config = rechargeConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("充值配置不存在");
        }
        return config;
    }
    
    /**
     * 创建充值配置
     * 
     * @param config 配置信息
     * @return 创建的配置
     */
    public RechargeConfig createConfig(RechargeConfig config) {
        // 检查站点ID是否已存在配置
        if (config.getSiteId() != null) {
            RechargeConfig existing = getConfigBySiteId(config.getSiteId());
            if (existing != null) {
                throw new RuntimeException("该站点的配置已存在");
            }
        }
        
        // 验证选项JSON格式
        validateOptionsJson(config.getOptionsJson());
        
        rechargeConfigMapper.insert(config);
        return config;
    }
    
    /**
     * 更新充值配置
     * 
     * @param id 配置ID
     * @param config 配置信息
     * @return 更新后的配置
     */
    public RechargeConfig updateConfig(Long id, RechargeConfig config) {
        RechargeConfig existing = getConfigById(id);
        
        // 如果站点ID改变，检查新站点ID是否已存在配置
        if (config.getSiteId() != null && !config.getSiteId().equals(existing.getSiteId())) {
            RechargeConfig duplicate = getConfigBySiteId(config.getSiteId());
            if (duplicate != null && !duplicate.getId().equals(id)) {
                throw new RuntimeException("该站点的配置已存在");
            }
            existing.setSiteId(config.getSiteId());
        }
        
        // 更新字段
        if (config.getExchangeRate() != null) {
            existing.setExchangeRate(config.getExchangeRate());
        }
        if (config.getMinAmount() != null) {
            existing.setMinAmount(config.getMinAmount());
        }
        if (config.getOptionsJson() != null) {
            // 验证选项JSON格式
            validateOptionsJson(config.getOptionsJson());
            existing.setOptionsJson(config.getOptionsJson());
        }
        if (config.getAllowCustom() != null) {
            existing.setAllowCustom(config.getAllowCustom());
        }
        if (config.getIsEnabled() != null) {
            existing.setIsEnabled(config.getIsEnabled());
        }
        
        rechargeConfigMapper.updateById(existing);
        return existing;
    }
    
    /**
     * 删除充值配置（逻辑删除）
     * 
     * @param id 配置ID
     */
    public void deleteConfig(Long id) {
        RechargeConfig config = getConfigById(id);
        config.setDeleted(1);
        rechargeConfigMapper.updateById(config);
    }
    
    /**
     * 验证选项JSON格式
     * 
     * @param optionsJson 选项JSON字符串
     */
    private void validateOptionsJson(String optionsJson) {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return;
        }
        
        try {
            List<RechargeOption> options = objectMapper.readValue(
                optionsJson,
                new TypeReference<List<RechargeOption>>() {}
            );
            
            // 验证每个选项
            for (RechargeOption option : options) {
                if (option.getPoints() == null || option.getPoints() <= 0) {
                    throw new RuntimeException("算力点数必须大于0");
                }
                if (option.getPrice() == null || option.getPrice() <= 0) {
                    throw new RuntimeException("价格必须大于0");
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("选项JSON格式错误：" + e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("验证选项JSON失败：" + e.getMessage());
        }
    }
    
    /**
     * 充值选项内部类（用于JSON验证）
     */
    private static class RechargeOption {
        private Integer points;
        private Integer price;
        
        public Integer getPoints() {
            return points;
        }
        
        public void setPoints(Integer points) {
            this.points = points;
        }
        
        public Integer getPrice() {
            return price;
        }
        
        public void setPrice(Integer price) {
            this.price = price;
        }
    }
}

