package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.config.MybatisPlusConfig;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.mapper.ApiInterfaceMapper;
import com.meitou.admin.mapper.ApiPlatformMapper;
import com.meitou.admin.util.AesEncryptUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API平台服务类
 */
@Service
@RequiredArgsConstructor
public class ApiPlatformService extends ServiceImpl<ApiPlatformMapper, ApiPlatform> {
    
    private final ApiPlatformMapper platformMapper; // 平台Mapper
    private final ApiInterfaceMapper interfaceMapper; // 接口Mapper
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取平台列表（按站点ID）
     * 注意：返回的平台中apiKey是加密的（用于管理端显示）
     * 
     * @param siteId 站点ID（可选）
     * @return 平台列表
     */
    public List<ApiPlatform> getPlatforms(Long siteId) {
        LambdaQueryWrapper<ApiPlatform> wrapper = new LambdaQueryWrapper<>();
        if (siteId != null) {
            wrapper.eq(ApiPlatform::getSiteId, siteId);
        }
        wrapper.eq(ApiPlatform::getDeleted, 0);
        wrapper.orderByDesc(ApiPlatform::getCreatedAt);
        List<ApiPlatform> platforms = platformMapper.selectList(wrapper);
        // 注意：这里不解密apiKey，因为管理端不需要看到明文
        return platforms;
    }
    
    /**
     * 获取平台列表（解密apiKey版本，用于内部服务调用）
     * 
     * @param siteId 站点ID（可选）
     * @return 平台列表（apiKey已解密）
     */
    public List<ApiPlatform> getPlatformsWithDecryptedKey(Long siteId) {
        List<ApiPlatform> platforms = getPlatforms(siteId);
        // 解密apiKey
        platforms.forEach(platform -> {
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                String decryptedKey = AesEncryptUtil.decrypt(platform.getApiKey());
                // 如果解密失败（返回null），说明可能是旧密钥加密的数据，设置为null
                platform.setApiKey(decryptedKey);
            }
        });
        return platforms;
    }
    
    /**
     * 根据类型获取平台列表（解密apiKey版本，用于内部服务调用）
     * 
     * @param type API类型：image_analysis, video_analysis, txt2img, img2img, txt2video, img2video, voice_clone, prompt_optimize
     * @param siteId 站点ID（可选）
     * @return 平台列表（apiKey已解密，已启用）
     */
    public List<ApiPlatform> getPlatformsByTypeWithDecryptedKey(String type, Long siteId) {
        LambdaQueryWrapper<ApiPlatform> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            wrapper.eq(ApiPlatform::getType, type);
        }
        if (siteId != null) {
            wrapper.eq(ApiPlatform::getSiteId, siteId);
        }
        wrapper.eq(ApiPlatform::getIsEnabled, true); // 只获取已启用的平台
        wrapper.eq(ApiPlatform::getDeleted, 0);
        wrapper.orderByDesc(ApiPlatform::getCreatedAt);
        
        List<ApiPlatform> platforms = platformMapper.selectList(wrapper);
        // 解密apiKey
        platforms.forEach(platform -> {
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                String decryptedKey = AesEncryptUtil.decrypt(platform.getApiKey());
                platform.setApiKey(decryptedKey);
            }
        });
        return platforms;
    }

    /**
     * 根据类型和模型获取平台（解密apiKey版本）
     * 优先匹配支持该模型的平台，其次返回未配置模型限制的平台
     *
     * @param type API类型
     * @param model 模型名称
     * @param siteId 站点ID（可选）
     * @return 匹配的平台，未找到返回null
     */
    public ApiPlatform getPlatformByTypeAndModel(String type, String model, Long siteId) {
        List<ApiPlatform> platforms = getPlatformsByTypeWithDecryptedKey(type, siteId);

        if (platforms.isEmpty()) {
            return null;
        }

        // 1. 优先查找明确支持该模型的平台
        if (model != null && !model.isEmpty()) {
            for (ApiPlatform platform : platforms) {
                String supportedModels = platform.getSupportedModels();
                if (supportedModels != null && !supportedModels.isEmpty()) {
                    try {
                        if (supportedModels.trim().startsWith("[")) {
                            // 尝试解析JSON (新格式)
                            JsonNode modelsNode = objectMapper.readTree(supportedModels);
                            for (JsonNode m : modelsNode) {
                                // 处理字符串数组 ["flux-1.0", "flux-2.0"]
                                if (m.isTextual()) {
                                    if (m.asText().equals(model)) {
                                        return platform;
                                    }
                                } 
                                // 处理对象数组 [{"name": "flux-1.0", "label": "Flux 1.0"}]
                                else if (m.isObject()) {
                                    // 优先匹配 name 字段（对应前端 ModelInfo.id）
                                    if (m.has("name") && m.get("name").asText().equals(model)) {
                                        return platform;
                                    }
                                    // 兼容 id 字段
                                    if (m.has("id") && m.get("id").asText().equals(model)) {
                                        return platform;
                                    }
                                    // 兼容 value 字段
                                    if (m.has("value") && m.get("value").asText().equals(model)) {
                                        return platform;
                                    }
                                }
                            }
                        } else {
                            // 旧格式兼容：#号分割
                            String[] models = supportedModels.split("#");
                            for (String m : models) {
                                if (m.trim().equals(model)) {
                                    return platform;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 解析JSON失败，尝试旧的#分割方式
                        String[] models = supportedModels.split("#");
                        for (String m : models) {
                            if (m.trim().equals(model)) {
                                return platform;
                            }
                        }
                    }
                }
            }
        }

        // 2. 如果没有明确支持的，查找未配置模型限制的平台（通用平台）
        for (ApiPlatform platform : platforms) {
            String supportedModels = platform.getSupportedModels();
            if (supportedModels == null || supportedModels.trim().isEmpty() || "[]".equals(supportedModels.trim())) {
                return platform;
            }
        }

        // 3. 如果还是没找到，且列表不为空，是否默认返回第一个？
        // 为了严格匹配，建议返回null，让上层决定是否报错
        return null;
    }
    
    /**
     * 创建平台（包含接口）
     * 
     * @param platform 平台信息
     * @param interfaces 接口列表
     * @return 创建的平台
     */
    @Transactional
    public ApiPlatform createPlatform(ApiPlatform platform, List<ApiInterface> interfaces) {
        // 设置默认值
        if (platform.getIsEnabled() == null) {
            platform.setIsEnabled(true);
        }
        
        // 加密apiKey（如果存在且未加密）
        if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
            if (!AesEncryptUtil.isEncrypted(platform.getApiKey())) {
                platform.setApiKey(AesEncryptUtil.encrypt(platform.getApiKey()));
            }
        }
        
        // 保存平台
        platformMapper.insert(platform);
        
        // 保存接口
        if (interfaces != null && !interfaces.isEmpty()) {
            for (ApiInterface apiInterface : interfaces) {
                apiInterface.setPlatformId(platform.getId());
                // 设置接口的siteId，从平台的siteId复制
                apiInterface.setSiteId(platform.getSiteId());
                interfaceMapper.insert(apiInterface);
            }
        }
        
        return platform;
    }
    
    /**
     * 更新平台（包含接口）
     * 
     * @param id 平台ID
     * @param platform 平台信息
     * @param interfaces 接口列表
     * @return 更新后的平台
     */
    @Transactional
    public ApiPlatform updatePlatform(Long id, ApiPlatform platform, List<ApiInterface> interfaces) {
        ApiPlatform existing = getPlatformById(id);
        
        // 更新平台信息
        if (platform.getName() != null) {
            existing.setName(platform.getName());
        }
        if (platform.getAlias() != null) {
            existing.setAlias(platform.getAlias());
        }
        if (platform.getSiteId() != null) {
            existing.setSiteId(platform.getSiteId());
        }
        if (platform.getNodeInfo() != null) {
            existing.setNodeInfo(platform.getNodeInfo());
        }
        if (platform.getIsEnabled() != null) {
            existing.setIsEnabled(platform.getIsEnabled());
        }
        if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
            // 检查apiKey是否已经加密过，如果已加密则不重复加密
            String apiKeyToSave = platform.getApiKey();
            if (!AesEncryptUtil.isEncrypted(apiKeyToSave)) {
                // 如果未加密，则加密
                apiKeyToSave = AesEncryptUtil.encrypt(apiKeyToSave);
            }
            existing.setApiKey(apiKeyToSave);
        }
        if (platform.getDescription() != null) {
            existing.setDescription(platform.getDescription());
        }
        if (platform.getSupportedModels() != null) {
            existing.setSupportedModels(platform.getSupportedModels());
        }
        if (platform.getType() != null) {
            existing.setType(platform.getType());
        }
        
        platformMapper.updateById(existing);
        
        // 删除旧接口
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getPlatformId, id);
        interfaceMapper.delete(wrapper);
        
        // 保存新接口
        if (interfaces != null && !interfaces.isEmpty()) {
            // 如果站点ID发生了变化，需要临时切换上下文，以确保接口插入到正确的站点
            // 注意：这里假设SiteContext是ThreadLocal管理的，修改后只影响当前线程的后续操作
            Long currentSiteId = SiteContext.getSiteId();
            if (currentSiteId != null && !currentSiteId.equals(existing.getSiteId())) {
                SiteContext.setSiteId(existing.getSiteId());
            }
            
            try {
                for (ApiInterface apiInterface : interfaces) {
                    apiInterface.setPlatformId(id);
                    // 设置接口的siteId，从平台的siteId复制
                    apiInterface.setSiteId(existing.getSiteId());
                    interfaceMapper.insert(apiInterface);
                }
            } finally {
                // 恢复上下文（虽然请求即将结束，但保持良好的编程习惯）
                if (currentSiteId != null && !currentSiteId.equals(existing.getSiteId())) {
                    SiteContext.setSiteId(currentSiteId);
                }
            }
        }
        
        return existing;
    }
    
    /**
     * 根据ID获取平台
     * 注意：返回的平台中apiKey是加密的（用于管理端显示）
     * 
     * @param id 平台ID
     * @return 平台
     */
    public ApiPlatform getPlatformById(Long id) {
        ApiPlatform platform = platformMapper.selectById(id);
        if (platform == null) {
            throw new RuntimeException("平台不存在");
        }
        return platform;
    }
    
    /**
     * 根据ID获取平台（解密apiKey版本，用于内部服务调用）
     * 
     * @param id 平台ID
     * @return 平台（apiKey已解密）
     */
    public ApiPlatform getPlatformByIdWithDecryptedKey(Long id) {
        ApiPlatform platform = getPlatformById(id);
        // 解密apiKey
        if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
            String decryptedKey = AesEncryptUtil.decrypt(platform.getApiKey());
            // 如果解密失败（返回null），说明可能是旧密钥加密的数据，设置为null
            platform.setApiKey(decryptedKey);
        }
        return platform;
    }
    
    /**
     * 获取平台的接口列表
     * 
     * @param platformId 平台ID
     * @return 接口列表
     */
    public List<ApiInterface> getInterfacesByPlatformId(Long platformId) {
        LambdaQueryWrapper<ApiInterface> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInterface::getPlatformId, platformId);
        wrapper.eq(ApiInterface::getDeleted, 0);
        return interfaceMapper.selectList(wrapper);
    }
    
    /**
     * 删除平台
     * 
     * @param id 平台ID
     */
    @Transactional
    public void deletePlatform(Long id) {
        getPlatformById(id);
        platformMapper.deleteById(id);
        // 接口会通过外键级联删除
    }
}

