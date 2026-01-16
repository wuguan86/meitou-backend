package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.entity.PaymentConfig;
import com.meitou.admin.mapper.PaymentConfigMapper;
import com.meitou.admin.util.AesEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 管理端支付配置服务类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentConfigService extends ServiceImpl<PaymentConfigMapper, PaymentConfig> {
    
    private final PaymentConfigMapper paymentConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 敏感字段集合，需要加密存储和脱敏显示
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
            "apiV3Key",
            "privateKey",
            "certContent",
            "wechatPayPublicKey",
            "appCertContent",
            "alipayRootCertContent",
            "alipayCertContent",
            "alipayPublicKey"
    ));
    
    /**
     * 获取支付配置列表（按站点ID）
     * 
     * @param siteId 站点ID
     * @return 支付配置列表
     */
    public List<PaymentConfig> getPaymentConfigs(Long siteId) {
        LambdaQueryWrapper<PaymentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentConfig::getSiteId, siteId);
        wrapper.eq(PaymentConfig::getDeleted, 0);
        wrapper.orderByAsc(PaymentConfig::getId);
        List<PaymentConfig> list = paymentConfigMapper.selectList(wrapper);
        
        // 脱敏处理
        list.forEach(this::maskPaymentConfig);
        return list;
    }
    
    /**
     * 根据支付类型和站点ID获取支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID
     * @return 支付配置
     */
    public PaymentConfig getPaymentConfigByType(String paymentType, Long siteId) {
        LambdaQueryWrapper<PaymentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentConfig::getPaymentType, paymentType);
        wrapper.eq(PaymentConfig::getSiteId, siteId);
        wrapper.eq(PaymentConfig::getDeleted, 0);
        PaymentConfig config = paymentConfigMapper.selectOne(wrapper);
        
        // 脱敏处理
        maskPaymentConfig(config);
        return config;
    }
    
    /**
     * 更新支付配置的启用状态
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID
     * @param isEnabled 是否启用
     * @return 更新后的支付配置
     */
    public PaymentConfig updatePaymentConfigStatus(String paymentType, Long siteId, Boolean isEnabled) {
        PaymentConfig config = getPaymentConfigByType(paymentType, siteId);
        if (config == null) {
            throw new RuntimeException("支付配置不存在：paymentType=" + paymentType + ", siteId=" + siteId);
        }
        config.setIsEnabled(isEnabled);
        paymentConfigMapper.updateById(config);
        return config;
    }
    
    /**
     * 创建或更新支付配置（如果不存在则创建，存在则更新）
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID
     * @param config 支付配置信息
     * @return 创建或更新后的支付配置
     */
    public PaymentConfig createOrUpdatePaymentConfig(String paymentType, Long siteId, PaymentConfig config) {
        PaymentConfig existing = getPaymentConfigByType(paymentType, siteId);
        
        // 如果存在，需要获取原始的加密数据用于对比
        String existingEncryptedJson = null;
        if (existing != null) {
            // 注意：getPaymentConfigByType已经对existing进行了脱敏，这里我们需要重新查询一次获取原始加密数据
            // 或者我们可以修改getPaymentConfigByType不脱敏，但那样会影响其他调用
            // 所以这里直接查数据库
            LambdaQueryWrapper<PaymentConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PaymentConfig::getId, existing.getId());
            PaymentConfig rawExisting = paymentConfigMapper.selectOne(wrapper);
            if (rawExisting != null) {
                existingEncryptedJson = rawExisting.getConfigJson();
            }
        }
        
        // 处理加密
        String encryptedJson = encryptConfigJson(config.getConfigJson(), existingEncryptedJson);
        
        if (existing == null) {
            // 创建新配置
            PaymentConfig newConfig = new PaymentConfig();
            newConfig.setPaymentType(paymentType);
            newConfig.setSiteId(siteId);
            newConfig.setConfigJson(encryptedJson);
            newConfig.setIsEnabled(config.getIsEnabled() != null ? config.getIsEnabled() : false);
            paymentConfigMapper.insert(newConfig);
            return newConfig;
        } else {
            // 更新现有配置
            if (config.getConfigJson() != null) {
                existing.setConfigJson(encryptedJson);
            }
            if (config.getIsEnabled() != null) {
                existing.setIsEnabled(config.getIsEnabled());
            }
            paymentConfigMapper.updateById(existing);
            return existing;
        }
    }
    
    /**
     * 更新支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID
     * @param config 支付配置信息
     * @return 更新后的支付配置
     */
    public PaymentConfig updatePaymentConfig(String paymentType, Long siteId, PaymentConfig config) {
        PaymentConfig existing = getPaymentConfigByType(paymentType, siteId);
        if (existing == null) {
            throw new RuntimeException("支付配置不存在：paymentType=" + paymentType + ", siteId=" + siteId);
        }
        
        // 获取原始加密数据
        LambdaQueryWrapper<PaymentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentConfig::getId, existing.getId());
        PaymentConfig rawExisting = paymentConfigMapper.selectOne(wrapper);
        String existingEncryptedJson = rawExisting != null ? rawExisting.getConfigJson() : null;
        
        if (config.getConfigJson() != null) {
            String encryptedJson = encryptConfigJson(config.getConfigJson(), existingEncryptedJson);
            existing.setConfigJson(encryptedJson);
        }
        if (config.getIsEnabled() != null) {
            existing.setIsEnabled(config.getIsEnabled());
        }
        
        paymentConfigMapper.updateById(existing);
        return existing;
    }
    
    /**
     * 删除支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID
     */
    public void deletePaymentConfig(String paymentType, Long siteId) {
        PaymentConfig config = getPaymentConfigByType(paymentType, siteId);
        if (config == null) {
            throw new RuntimeException("支付配置不存在：paymentType=" + paymentType + ", siteId=" + siteId);
        }
        paymentConfigMapper.deleteById(config.getId());
    }

    // ================== 私有辅助方法 ==================

    /**
     * 对支付配置进行脱敏处理
     */
    private void maskPaymentConfig(PaymentConfig config) {
        if (config == null || config.getConfigJson() == null) {
            return;
        }
        try {
            Map<String, String> map = objectMapper.readValue(config.getConfigJson(), new TypeReference<Map<String, String>>() {});
            boolean changed = false;
            for (String key : SENSITIVE_FIELDS) {
                if (map.containsKey(key)) {
                    String encrypted = map.get(key);
                    // 解密
                    String plaintext = AesEncryptUtil.decrypt(encrypted);
                    // 脱敏
                    if (plaintext != null) {
                        map.put(key, maskString(plaintext));
                        changed = true;
                    }
                }
            }
            if (changed) {
                config.setConfigJson(objectMapper.writeValueAsString(map));
            }
        } catch (Exception e) {
            log.error("支付配置脱敏失败", e);
        }
    }

    /**
     * 加密配置JSON
     * @param newJson 新的配置JSON（包含明文或掩码）
     * @param existingEncryptedJson 数据库中现有的加密JSON
     * @return 加密后的JSON
     */
    private String encryptConfigJson(String newJson, String existingEncryptedJson) {
        if (newJson == null) return null;
        
        try {
            Map<String, String> newMap = objectMapper.readValue(newJson, new TypeReference<Map<String, String>>() {});
            Map<String, String> existingMap = new HashMap<>();
            
            if (existingEncryptedJson != null && !existingEncryptedJson.isEmpty()) {
                try {
                    existingMap = objectMapper.readValue(existingEncryptedJson, new TypeReference<Map<String, String>>() {});
                } catch (Exception e) {
                    log.warn("解析现有配置JSON失败", e);
                }
            }
            
            for (String key : SENSITIVE_FIELDS) {
                if (newMap.containsKey(key)) {
                    String val = newMap.get(key);
                    // 检查是否为掩码（包含****）或者为空
                    if (val == null || val.contains("****")) {
                        // 如果是掩码，尝试保留原有加密值
                        if (existingMap.containsKey(key)) {
                            newMap.put(key, existingMap.get(key));
                        } else {
                            // 如果没有原有值，且新值为掩码，这通常不应该发生，除非前端逻辑错误
                            // 这里我们保留原值（即掩码），或者设为空？
                            // 假设包含****的值是无效的输入，如果无法恢复原值，则不保存该字段或保存空
                            // 为了安全，如果无法恢复，我们就不更新这个字段（如果它在newMap中）
                            // 但如果newMap是全量更新，我们必须放点什么。
                            // 此时我们假设如果不修改，前端应该传空或者掩码。
                            // 如果是掩码且没旧值，说明数据不一致，记录日志
                            log.warn("Field {} contains mask but no existing value found", key);
                            newMap.remove(key); // 移除该字段，避免保存掩码
                        }
                    } else {
                        // 是明文，进行加密
                        if (!val.isEmpty()) {
                            newMap.put(key, AesEncryptUtil.encrypt(val));
                        }
                    }
                }
            }
            return objectMapper.writeValueAsString(newMap);
        } catch (Exception e) {
            throw new RuntimeException("处理支付配置加密失败", e);
        }
    }

    /**
     * 字符串脱敏：保留前4后4，中间替换为****
     */
    private String maskString(String str) {
        if (str == null || str.isEmpty()) return "";
        if (str.length() <= 8) return "******";
        return str.substring(0, 4) + "****" + str.substring(str.length() - 4);
    }
}

