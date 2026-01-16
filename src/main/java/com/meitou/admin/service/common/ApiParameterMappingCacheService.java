package com.meitou.admin.service.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meitou.admin.entity.ApiParameterMapping;
import com.meitou.admin.mapper.ApiParameterMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 参数映射缓存服务
 * 在应用启动时加载，并在数据变更时刷新
 */
@Slf4j
@Service
public class ApiParameterMappingCacheService implements CommandLineRunner {

    private final ApiParameterMappingMapper apiParameterMappingMapper;

    // 缓存: PlatformId -> List<ApiParameterMapping>
    private Map<Long, List<ApiParameterMapping>> cache = new ConcurrentHashMap<>();

    public ApiParameterMappingCacheService(ApiParameterMappingMapper apiParameterMappingMapper) {
        this.apiParameterMappingMapper = apiParameterMappingMapper;
    }

    @Override
    public void run(String... args) {
        refresh();
    }

    /**
     * 刷新缓存
     */
    public synchronized void refresh() {
        log.info("开始刷新参数映射缓存...");
        try {
            // 使用自定义方法查询所有数据，忽略租户限制
            List<ApiParameterMapping> all = apiParameterMappingMapper.selectAllIgnoreTenant();
            
            if (all == null || all.isEmpty()) {
                this.cache = new ConcurrentHashMap<>();
                log.info("参数映射缓存已清空（无数据）");
                return;
            }

            // 按 platformId 分组
            Map<Long, List<ApiParameterMapping>> newCache = all.stream()
                    .collect(Collectors.groupingBy(ApiParameterMapping::getPlatformId));
            
            this.cache = new ConcurrentHashMap<>(newCache);
            log.info("参数映射缓存刷新完成，共加载 {} 个平台的配置", newCache.size());
        } catch (Exception e) {
            log.error("刷新参数映射缓存失败", e);
        }
    }

    /**
     * 获取指定平台和模型的参数映射配置
     * @param platformId 平台ID
     * @param model 模型名称
     * @return 映射列表（已排序：通用在前，特定模型在后）
     */
    public List<ApiParameterMapping> getMappings(Long platformId, String model) {
        if (platformId == null) {
            return Collections.emptyList();
        }

        List<ApiParameterMapping> platformMappings = cache.get(platformId);
        if (platformMappings == null || platformMappings.isEmpty()) {
            return Collections.emptyList();
        }

        // 筛选匹配的映射（通用 或 指定模型）
        List<ApiParameterMapping> matched = platformMappings.stream()
                .filter(m -> isModelMatch(m, model))
                .collect(Collectors.toList());

        // 排序：通用配置在前，特定模型在后（以便后续处理时特定模型覆盖通用配置）
        matched.sort((a, b) -> {
            boolean aIsGeneral = isGeneral(a);
            boolean bIsGeneral = isGeneral(b);
            
            if (aIsGeneral && !bIsGeneral) return -1;
            if (!aIsGeneral && bIsGeneral) return 1;
            return 0;
        });

        return matched;
    }

    private boolean isModelMatch(ApiParameterMapping m, String model) {
        // 如果映射配置是通用的，则匹配任何模型
        if (isGeneral(m)) {
            return true;
        }
        // 否则必须精确匹配模型名称
        return model != null && model.equals(m.getModelName());
    }

    private boolean isGeneral(ApiParameterMapping m) {
        return m.getModelName() == null || m.getModelName().isEmpty() || "通用".equals(m.getModelName());
    }
}
