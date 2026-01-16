package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.MarketingAd;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.mapper.MarketingAdMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 管理端营销广告服务类
 */
@Service
@RequiredArgsConstructor
public class MarketingAdService extends ServiceImpl<MarketingAdMapper, MarketingAd> {
    
    private final MarketingAdMapper adMapper;
    private final FileStorageService fileStorageService;
    
    /**
     * 获取广告列表（按站点分类）
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 广告列表
     */
    public List<MarketingAd> getAds() {
        LambdaQueryWrapper<MarketingAd> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(MarketingAd::getPosition);
        List<MarketingAd> ads = adMapper.selectList(wrapper);
        
        // 处理签名URL
        if (ads != null && !ads.isEmpty()) {
            for (MarketingAd ad : ads) {
                ad.setImageUrl(fileStorageService.getFileUrl(ad.getImageUrl()));
            }
        }
        
        return ads;
    }
    
    /**
     * 获取指定站点的广告列表（管理后台使用）
     * 
     * @param siteId 站点ID
     * @return 广告列表
     */
    public List<MarketingAd> getAdsBySiteId(Long siteId) {
        LambdaQueryWrapper<MarketingAd> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketingAd::getSiteId, siteId);
        wrapper.orderByAsc(MarketingAd::getPosition);
        List<MarketingAd> ads = adMapper.selectList(wrapper);
        
        // 处理签名URL
        if (ads != null && !ads.isEmpty()) {
            for (MarketingAd ad : ads) {
                ad.setImageUrl(fileStorageService.getFileUrl(ad.getImageUrl()));
            }
        }
        
        return ads;
    }
    
    /**
     * 获取用户端有效的广告列表（根据时间、激活状态、全屏标识过滤）
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 有效的广告列表（已按position排序）
     */
    public List<MarketingAd> getActiveAds() {
        LocalDate today = LocalDate.now(); // 获取当前日期
        
        LambdaQueryWrapper<MarketingAd> wrapper = new LambdaQueryWrapper<>();
        
        // 过滤条件：激活状态
        wrapper.eq(MarketingAd::getIsActive, true);
        
        // 过滤条件：全屏广告
        wrapper.eq(MarketingAd::getIsFullScreen, true);
        
        // 过滤条件：开始时间 <= 今天 <= 结束时间
        wrapper.le(MarketingAd::getStartDate, today);
        wrapper.ge(MarketingAd::getEndDate, today);
        
        // 按position升序排序（数字越小排序越靠前）
        wrapper.orderByAsc(MarketingAd::getPosition);
        
        List<MarketingAd> ads = adMapper.selectList(wrapper);
        
        // 处理签名URL
        if (ads != null && !ads.isEmpty()) {
            for (MarketingAd ad : ads) {
                ad.setImageUrl(fileStorageService.getFileUrl(ad.getImageUrl()));
            }
        }
        
        return ads;
    }
    
    /**
     * 创建广告
     * 
     * @param ad 广告信息
     * @return 创建的广告
     */
    public MarketingAd createAd(MarketingAd ad) {
        checkPositionDuplicate(ad.getSiteId(), ad.getPosition(), null);
        adMapper.insert(ad);
        
        // 返回前处理签名URL
        ad.setImageUrl(fileStorageService.getFileUrl(ad.getImageUrl()));
        
        return ad;
    }
    
    /**
     * 更新广告
     * 
     * @param id 广告ID
     * @param ad 广告信息
     * @return 更新后的广告
     */
    public MarketingAd updateAd(Long id, MarketingAd ad) {
        MarketingAd existing = getAdById(id);
        
        if (ad.getTitle() != null) existing.setTitle(ad.getTitle());
        if (ad.getImageUrl() != null) existing.setImageUrl(ad.getImageUrl());
        if (ad.getStartDate() != null) existing.setStartDate(ad.getStartDate());
        if (ad.getEndDate() != null) existing.setEndDate(ad.getEndDate());
        if (ad.getLinkType() != null) existing.setLinkType(ad.getLinkType());
        if (ad.getLinkUrl() != null) existing.setLinkUrl(ad.getLinkUrl());
        if (ad.getRichContent() != null) existing.setRichContent(ad.getRichContent());
        if (ad.getSummary() != null) existing.setSummary(ad.getSummary());
        if (ad.getTags() != null) existing.setTags(ad.getTags());
        if (ad.getIsActive() != null) existing.setIsActive(ad.getIsActive());
        if (ad.getPosition() != null) {
            if (!ad.getPosition().equals(existing.getPosition())) {
                checkPositionDuplicate(existing.getSiteId(), ad.getPosition(), id);
            }
            existing.setPosition(ad.getPosition());
        }
        if (ad.getIsFullScreen() != null) existing.setIsFullScreen(ad.getIsFullScreen());
        
        adMapper.updateById(existing);
        
        // 返回前处理签名URL
        existing.setImageUrl(fileStorageService.getFileUrl(existing.getImageUrl()));
        
        return existing;
    }
    
    /**
     * 根据ID获取广告
     * 
     * @param id 广告ID
     * @return 广告
     */
    public MarketingAd getAdById(Long id) {
        MarketingAd ad = adMapper.selectById(id);
        if (ad == null) {
            throw new RuntimeException("广告不存在");
        }
        
        // 处理签名URL
        ad.setImageUrl(fileStorageService.getFileUrl(ad.getImageUrl()));
        
        return ad;
    }
    
    /**
     * 删除广告
     * 
     * @param id 广告ID
     */
    public void deleteAd(Long id) {
        getAdById(id);
        adMapper.deleteById(id);
    }

    /**
     * 检查广告位位置是否重复
     * 
     * @param siteId 站点ID
     * @param position 广告位位置
     * @param excludeId 排除的广告ID（更新时使用）
     */
    private void checkPositionDuplicate(Long siteId, Integer position, Long excludeId) {
        if (siteId == null || position == null) {
            return;
        }
        LambdaQueryWrapper<MarketingAd> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MarketingAd::getSiteId, siteId);
        wrapper.eq(MarketingAd::getPosition, position);
        if (excludeId != null) {
            wrapper.ne(MarketingAd::getId, excludeId);
        }
        if (adMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该站点广告位位置 " + position + " 已存在，不能重复");
        }
    }
}

