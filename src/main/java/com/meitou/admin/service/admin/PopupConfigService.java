package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.PopupConfig;
import com.meitou.admin.mapper.PopupConfigMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端营销弹窗配置服务类
 */
@Service
@RequiredArgsConstructor
public class PopupConfigService extends ServiceImpl<PopupConfigMapper, PopupConfig> {

    private final PopupConfigMapper popupConfigMapper;
    private final FileStorageService fileStorageService;

    /**
     * 获取指定站点的弹窗配置列表
     * 
     * @param siteId 站点ID
     * @return 弹窗配置列表
     */
    public List<PopupConfig> getPopupListBySiteId(Long siteId) {
        LambdaQueryWrapper<PopupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PopupConfig::getSiteId, siteId);
        wrapper.orderByDesc(PopupConfig::getCreatedAt);
        List<PopupConfig> list = popupConfigMapper.selectList(wrapper);
        
        // 处理图片URL
        list.forEach(config -> {
            if (config.getImageUrl() != null) {
                config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
            }
        });
        
        return list;
    }

    /**
     * 创建弹窗配置
     * 
     * @param config 弹窗配置
     * @return 创建后的配置
     */
    public PopupConfig createPopup(PopupConfig config) {
        popupConfigMapper.insert(config);
        
        // 处理图片URL
        if (config.getImageUrl() != null) {
            config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
        }
        
        return config;
    }

    /**
     * 更新弹窗配置
     * 
     * @param config 弹窗配置
     * @return 更新后的配置
     */
    public PopupConfig updatePopup(PopupConfig config) {
        popupConfigMapper.updateById(config);
        
        // 处理图片URL
        if (config.getImageUrl() != null) {
            config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
        }
        
        return config;
    }

    public PopupConfig getPopupConfigBySiteId(Long siteId) {
        LambdaQueryWrapper<PopupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PopupConfig::getSiteId, siteId);
        wrapper.orderByDesc(PopupConfig::getCreatedAt);
        wrapper.last("limit 1");
        PopupConfig config = popupConfigMapper.selectOne(wrapper);
        if (config != null && config.getImageUrl() != null) {
            config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
        }
        return config;
    }

    public PopupConfig saveOrUpdatePopupConfig(PopupConfig config) {
        PopupConfig existing = null;
        if (config.getId() != null) {
            existing = popupConfigMapper.selectById(config.getId());
        }
        if (existing == null && config.getSiteId() != null) {
            LambdaQueryWrapper<PopupConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(PopupConfig::getSiteId, config.getSiteId());
            wrapper.orderByDesc(PopupConfig::getCreatedAt);
            wrapper.last("limit 1");
            existing = popupConfigMapper.selectOne(wrapper);
        }

        if (existing == null) {
            popupConfigMapper.insert(config);
        } else {
            if (config.getName() != null) existing.setName(config.getName());
            if (config.getImageUrl() != null) existing.setImageUrl(config.getImageUrl());
            if (config.getIsEnabled() != null) existing.setIsEnabled(config.getIsEnabled());
            if (config.getJumpType() != null) existing.setJumpType(config.getJumpType());
            if (config.getJumpLink() != null) existing.setJumpLink(config.getJumpLink());
            if (config.getRichTextContent() != null) existing.setRichTextContent(config.getRichTextContent());
            if (config.getStartDate() != null) existing.setStartDate(config.getStartDate());
            if (config.getEndDate() != null) existing.setEndDate(config.getEndDate());
            popupConfigMapper.updateById(existing);
            config = existing;
        }

        if (config.getImageUrl() != null) {
            config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
        }
        return config;
    }

    /**
     * 获取指定站点的启用弹窗配置列表
     * 
     * @param siteId 站点ID
     * @return 弹窗配置列表
     */
    public List<PopupConfig> getActivePopups(Long siteId) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PopupConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PopupConfig::getSiteId, siteId);
        wrapper.eq(PopupConfig::getIsEnabled, true);
        wrapper.isNotNull(PopupConfig::getStartDate);
        wrapper.isNotNull(PopupConfig::getEndDate);
        wrapper.le(PopupConfig::getStartDate, today);
        wrapper.ge(PopupConfig::getEndDate, today);
        wrapper.orderByDesc(PopupConfig::getCreatedAt);
        List<PopupConfig> list = popupConfigMapper.selectList(wrapper);
        
        // 处理图片URL
        list.forEach(config -> {
            if (config.getImageUrl() != null) {
                config.setImageUrl(fileStorageService.getFileUrl(config.getImageUrl()));
            }
        });
        
        return list;
    }

    /**
     * 删除弹窗配置
     * 
     * @param id 弹窗ID
     */
    public void deletePopup(Long id) {
        popupConfigMapper.deleteById(id);
    }
}
