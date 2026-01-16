package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.UserAsset;
import com.meitou.admin.mapper.UserAssetMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.entity.User;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户端资产服务类
 * 处理用户资产的上传、查询、删除等业务逻辑
 */
@Service
@RequiredArgsConstructor
public class UserAssetAppService extends ServiceImpl<UserAssetMapper, UserAsset> {
    
    private final UserAssetMapper assetMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;
    
    /**
     * 上传资产（图片、视频、音频）
     * 
     * @param userId 用户ID
     * @param title 标题
     * @param type 类型：image-图片，video-视频，audio-音频
     * @param url 文件URL
     * @param thumbnail 缩略图URL（可选）
     * @param folder 文件夹路径（可选）
     * @return 创建的资产
     */
    @Transactional
    public UserAsset uploadAsset(Long userId, String title, String type, 
                                  String url, String thumbnail, String folder) {
        // 从SiteContext获取当前站点ID
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.SITE_NOT_FOUND);
        }
        
        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 创建资产对象
        UserAsset asset = new UserAsset();
        asset.setTitle(title);
        asset.setType(type);
        asset.setSiteId(siteId);
        asset.setUrl(url);
        
        // 如果是视频且没有缩略图，尝试生成
        if ("video".equals(type) && (thumbnail == null || thumbnail.isEmpty())) {
            if (url != null) {
                if (url.contains("aliyuncs.com")) {
                    thumbnail = url + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
                } else if (url.contains("myqcloud.com") || url.contains("cos")) {
                    thumbnail = url + "?ci-process=snapshot&time=1&format=jpg";
                }
            }
        }
        asset.setThumbnail(thumbnail);
        asset.setFolder(folder);
        asset.setUserId(userId);
        asset.setUserName(user.getUsername());
        asset.setStatus("published"); // 默认状态为展示中
        asset.setIsPinned(false); // 默认不置顶
        asset.setLikeCount(0); // 默认点赞数为0
        asset.setUploadDate(LocalDateTime.now()); // 设置上传时间
        
        // 保存到数据库
        assetMapper.insert(asset);
        
        // 返回前处理签名URL
        asset.setUrl(fileStorageService.getFileUrl(asset.getUrl()));
        asset.setThumbnail(fileStorageService.getFileUrl(asset.getThumbnail()));
        
        return asset;
    }
    
    /**
     * 获取用户的资产列表（支持文件夹筛选和类型筛选）
     * 
     * @param userId 用户ID
     * @param folder 文件夹路径（可选，如果为null或空字符串则返回根目录的资产）
     * @param type 类型筛选（可选：image、video、audio，如果为null或"all"则返回所有类型）
     * @param ignoreFolder 是否忽略文件夹结构（true表示返回所有层级的资产）
     * @return 资产列表
     */
    public List<UserAsset> getUserAssets(Long userId, String folder, String type, Boolean ignoreFolder) {
        LambdaQueryWrapper<UserAsset> wrapper = new LambdaQueryWrapper<>();
        
        // 筛选用户
        wrapper.eq(UserAsset::getUserId, userId);
        
        // 筛选文件夹：只有在不忽略文件夹结构时才生效
        if (Boolean.TRUE.equals(ignoreFolder)) {
            // 忽略文件夹，返回所有资产
        } else if (folder == null || folder.trim().isEmpty()) {
            wrapper.and(w -> w.isNull(UserAsset::getFolder).or().eq(UserAsset::getFolder, ""));
        } else {
            wrapper.eq(UserAsset::getFolder, folder);
        }
        
        // 筛选类型
        if (StringUtils.hasText(type) && !"all".equals(type)) {
            wrapper.eq(UserAsset::getType, type);
        }
        
        // 按上传时间倒序排列
        wrapper.orderByDesc(UserAsset::getUploadDate);
        
        List<UserAsset> assets = assetMapper.selectList(wrapper);
        
        // 处理签名URL
        if (assets != null && !assets.isEmpty()) {
            for (UserAsset asset : assets) {
                // 如果是视频且没有缩略图，尝试生成
                if ("video".equals(asset.getType()) && (asset.getThumbnail() == null || asset.getThumbnail().isEmpty())) {
                    String u = asset.getUrl();
                    if (u != null) {
                        if (u.contains("aliyuncs.com")) {
                            asset.setThumbnail(u + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast");
                        } else if (u.contains("myqcloud.com") || u.contains("cos")) {
                            asset.setThumbnail(u + "?ci-process=snapshot&time=1&format=jpg");
                        }
                    }
                }
                
                asset.setUrl(fileStorageService.getFileUrl(asset.getUrl()));
                asset.setThumbnail(fileStorageService.getFileUrl(asset.getThumbnail()));
            }
        }
        
        return assets;
    }
    
    /**
     * 获取用户的资产列表（兼容旧方法）
     */
    public List<UserAsset> getUserAssets(Long userId, String folder, String type) {
        return getUserAssets(userId, folder, type, false);
    }
    
    /**
     * 获取用户的所有文件夹路径列表（从资产中提取，用于兼容旧代码）
     * 注意：新代码应使用 AssetFolderAppService.getFolders() 方法
     * 
     * @param userId 用户ID
     * @return 文件夹路径列表
     */
    @Deprecated
    public List<String> getUserFolders(Long userId) {
        LambdaQueryWrapper<UserAsset> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAsset::getUserId, userId);
        wrapper.isNotNull(UserAsset::getFolder);
        wrapper.ne(UserAsset::getFolder, "");
        wrapper.select(UserAsset::getFolder);
        wrapper.groupBy(UserAsset::getFolder);
        
        List<UserAsset> assets = assetMapper.selectList(wrapper);
        return assets.stream()
                .map(UserAsset::getFolder)
                .filter(f -> f != null && !f.trim().isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * 更新资产信息
     * 
     * @param userId 用户ID
     * @param assetId 资产ID
     * @param title 新标题（可选）
     * @param folder 新文件夹路径（可选）
     * @return 更新后的资产
     */
    @Transactional
    public UserAsset updateAsset(Long userId, Long assetId, String title, String folder) {
        // 查询资产并验证所有权
        UserAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }
        if (!asset.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权操作此资产");
        }
        
        // 更新字段
        if (title != null) {
            asset.setTitle(title);
        }
        if (folder != null) {
            asset.setFolder(folder);
        }
        
        assetMapper.updateById(asset);
        return asset;
    }
    
    /**
     * 删除资产（逻辑删除）
     * 
     * @param userId 用户ID
     * @param assetId 资产ID
     */
    @Transactional
    public void deleteAsset(Long userId, Long assetId) {
        // 查询资产并验证所有权
        UserAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }
        if (!asset.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权操作此资产");
        }
        
        // 逻辑删除
        assetMapper.deleteById(assetId);
    }
    
    /**
     * 批量删除资产（逻辑删除）
     * 
     * @param userId 用户ID
     * @param assetIds 资产ID列表
     */
    @Transactional
    public void deleteAssets(Long userId, List<Long> assetIds) {
        for (Long assetId : assetIds) {
            deleteAsset(userId, assetId);
        }
    }
}
