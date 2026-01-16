package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.dto.ApiParameterMappingRequest;
import com.meitou.admin.dto.ApiParameterMappingResponse;
import com.meitou.admin.entity.ApiParameterMapping;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.ApiParameterMappingMapper;
import com.meitou.admin.mapper.ApiPlatformMapper;
import com.meitou.admin.service.common.ApiParameterMappingCacheService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApiParameterMappingService {

    private final ApiParameterMappingMapper apiParameterMappingMapper;
    private final ApiPlatformMapper apiPlatformMapper;
    private final ApiParameterMappingCacheService apiParameterMappingCacheService;

    public ApiParameterMappingService(ApiParameterMappingMapper apiParameterMappingMapper, 
                                      ApiPlatformMapper apiPlatformMapper,
                                      ApiParameterMappingCacheService apiParameterMappingCacheService) {
        this.apiParameterMappingMapper = apiParameterMappingMapper;
        this.apiPlatformMapper = apiPlatformMapper;
        this.apiParameterMappingCacheService = apiParameterMappingCacheService;
    }

    /**
     * 分页查询
     */
    public Page<ApiParameterMappingResponse> list(Integer page, Integer size, Long platformId, String modelName) {
        Page<ApiParameterMapping> pageParam = new Page<>(page, size);
        QueryWrapper<ApiParameterMapping> queryWrapper = new QueryWrapper<>();
        
        // 多租户过滤
        Long currentSiteId = SiteContext.getSiteId();
        if (currentSiteId != null && currentSiteId != 0) {
            queryWrapper.eq("site_id", currentSiteId);
        }

        if (platformId != null) {
            queryWrapper.eq("platform_id", platformId);
        }
        if (modelName != null && !modelName.isEmpty()) {
            queryWrapper.eq("model_name", modelName);
        }
        
        queryWrapper.orderByDesc("created_at");

        Page<ApiParameterMapping> result = apiParameterMappingMapper.selectPage(pageParam, queryWrapper);
        
        // 获取所有平台信息用于填充名称
        List<Long> platformIds = result.getRecords().stream().map(ApiParameterMapping::getPlatformId).distinct().collect(Collectors.toList());
        Map<Long, String> platformNames;
        if (!platformIds.isEmpty()) {
            platformNames = apiPlatformMapper.selectBatchIds(platformIds).stream()
                    .collect(Collectors.toMap(ApiPlatform::getId, ApiPlatform::getName));
        } else {
            platformNames = Map.of();
        }

        Map<Long, String> finalPlatformNames = platformNames;
        Page<ApiParameterMappingResponse> responsePage = new Page<>();
        BeanUtils.copyProperties(result, responsePage, "records");
        
        List<ApiParameterMappingResponse> list = result.getRecords().stream().map(item -> {
            ApiParameterMappingResponse resp = new ApiParameterMappingResponse();
            BeanUtils.copyProperties(item, resp);
            resp.setPlatformName(finalPlatformNames.get(item.getPlatformId()));
            return resp;
        }).collect(Collectors.toList());
        
        responsePage.setRecords(list);
        return responsePage;
    }

    /**
     * 创建
     */
    @Transactional
    public void create(ApiParameterMappingRequest request) {
        ApiParameterMapping entity = new ApiParameterMapping();
        BeanUtils.copyProperties(request, entity);
        
        // 处理租户
        if (entity.getSiteId() == null) {
            entity.setSiteId(SiteContext.getSiteId());
        }
        
        apiParameterMappingMapper.insert(entity);
        
        // 刷新缓存
        apiParameterMappingCacheService.refresh();
    }

    /**
     * 更新
     */
    @Transactional
    public void update(Long id, ApiParameterMappingRequest request) {
        ApiParameterMapping entity = apiParameterMappingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "记录不存在");
        }
        
        BeanUtils.copyProperties(request, entity);
        entity.setId(id); // 确保ID不被覆盖
        
        apiParameterMappingMapper.updateById(entity);
        
        // 刷新缓存
        apiParameterMappingCacheService.refresh();
    }

    /**
     * 删除
     */
    @Transactional
    public void delete(Long id) {
        apiParameterMappingMapper.deleteById(id);
        
        // 刷新缓存
        apiParameterMappingCacheService.refresh();
    }

    /**
     * 获取详情
     */
    public ApiParameterMappingResponse get(Long id) {
        ApiParameterMapping entity = apiParameterMappingMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "记录不存在");
        }
        
        ApiParameterMappingResponse response = new ApiParameterMappingResponse();
        BeanUtils.copyProperties(entity, response);
        
        ApiPlatform platform = apiPlatformMapper.selectById(entity.getPlatformId());
        if (platform != null) {
            response.setPlatformName(platform.getName());
        }
        
        return response;
    }
}
