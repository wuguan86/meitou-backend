package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.UserAsset;
import com.meitou.admin.mapper.UserAssetMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端用户资产服务类
 */
@Service
@RequiredArgsConstructor
public class UserAssetService extends ServiceImpl<UserAssetMapper, UserAsset> {
    
    private final UserAssetMapper assetMapper;
    private final FileStorageService fileStorageService;
    
    /**
     * 获取资产列表（支持类型、搜索、分页）
     * siteId 由 MyBatis Plus 多租户插件自动处理（基于 @SiteScope 设置的上下文）
     * 
     * @param page 分页对象
     * @param type 类型
     * @param search 搜索关键词
     * @return 资产列表分页结果
     */
    public IPage<UserAsset> getAssets(Page<UserAsset> page, String type, String search) {
        LambdaQueryWrapper<UserAsset> wrapper = new LambdaQueryWrapper<>();
        
        // siteId 由插件自动处理，无需手动添加
        
        if (StringUtils.hasText(type) && !"all".equals(type)) {
            wrapper.eq(UserAsset::getType, type);
        }
        if (StringUtils.hasText(search)) {
            wrapper.and(w -> w.like(UserAsset::getTitle, search)
                    .or().like(UserAsset::getUserName, search));
        }
        
        wrapper.orderByDesc(UserAsset::getCreatedAt);
        
        IPage<UserAsset> result = assetMapper.selectPage(page, wrapper);
        if (result != null && result.getRecords() != null) {
            for (UserAsset asset : result.getRecords()) {
                if (asset == null) {
                    continue;
                }
                asset.setUrl(fileStorageService.getFileUrl(asset.getUrl()));
                asset.setThumbnail(fileStorageService.getFileUrl(asset.getThumbnail()));
            }
        }
        return result;
    }

    /**
     * 根据ID获取资产
     * 
     * @param id 资产ID
     * @return 资产
     */
    public UserAsset getAssetById(Long id) {
        UserAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new RuntimeException("资产不存在");
        }
        return asset;
    }
    
    /**
     * 更新资产
     * 
     * @param id 资产ID
     * @param asset 资产信息
     * @return 更新后的资产
     */
    public UserAsset updateAsset(Long id, UserAsset asset) {
        UserAsset existing = getAssetById(id);
        
        if (asset.getTitle() != null) {
            existing.setTitle(asset.getTitle());
        }
        if (asset.getStatus() != null) {
            existing.setStatus(asset.getStatus());
        }
        
        assetMapper.updateById(existing);
        return existing;
    }
    
    /**
     * 删除资产（逻辑删除）
     * 
     * @param id 资产ID
     */
    public void deleteAsset(Long id) {
        getAssetById(id);
        assetMapper.deleteById(id);
    }
    
//    /**
//     * 置顶/取消置顶
//     *
//     * @param id 资产ID
//     * @return 更新后的资产
//     */
//    public UserAsset togglePin(Long id) {
//        UserAsset asset = getAssetById(id);
//        asset.setIsPinned(!asset.getIsPinned());
//        assetMapper.updateById(asset);
//        return asset;
//    }
    
    /**
     * 更新状态（上架/下架）
     * 
     * @param id 资产ID
     * @param status 状态
     * @return 更新后的资产
     */
    public UserAsset updateStatus(Long id, String status) {
        UserAsset asset = getAssetById(id);
        asset.setStatus(status);
        assetMapper.updateById(asset);
        return asset;
    }
    
    /**
     * 更新点赞数
     * 
     * @param id 资产ID
     * @param likeCount 点赞数
     * @return 更新后的资产
     */
    public UserAsset updateLikeCount(Long id, Integer likeCount) {
        UserAsset asset = getAssetById(id);
        asset.setLikeCount(likeCount);
        assetMapper.updateById(asset);
        return asset;
    }
}

