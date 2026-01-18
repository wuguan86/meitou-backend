package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.entity.PublishedContent;
import com.meitou.admin.entity.User;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.mapper.PublishedContentMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 发布内容服务类
 * 处理发布内容的发布、查询、管理等业务逻辑
 */
@Service
@RequiredArgsConstructor
public class PublishedContentService extends ServiceImpl<PublishedContentMapper, PublishedContent> {
    
    private final PublishedContentMapper contentMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;
    private final FileStorageService fileStorageService;
    private final GenerationRecordMapper generationRecordMapper;
    
    private String generateThumbnailUrl(String contentUrl, String fileType) {
        if (contentUrl == null || contentUrl.isEmpty()) {
            return null;
        }
        
        // 如果已经是带参数的URL，就不再处理
        if (contentUrl.contains("?x-oss-process=") || contentUrl.contains("?ci-process=") || contentUrl.contains("?imageMogr2")) {
            return contentUrl;
        }

        if (contentUrl.contains("aliyuncs.com")) {
            if ("video".equals(fileType)) {
                return contentUrl + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
            } else {
                // 图片缩略图，宽300
                return contentUrl + "?x-oss-process=image/resize,w_300";
            }
        } else if (contentUrl.contains("myqcloud.com") || contentUrl.contains("cos")) {
            if ("video".equals(fileType)) {
                return contentUrl + "?ci-process=snapshot&time=1&format=jpg";
            } else {
                // 腾讯云图片缩略图
                return contentUrl + "?imageMogr2/thumbnail/300x";
            }
        }
        
        return contentUrl;
    }

    /**
     * 发布内容
     * 
     * @param userId 用户ID
     * @param title 标题
     * @param description 描述
     * @param contentUrl 内容URL
     * @param thumbnail 缩略图URL
     * @param type 类型：image-图片，video-视频
     * @param generationType 生成类型：txt2img/img2img/txt2video/img2video
     * @param generationConfig 生成配置参数（JSON字符串）
     * @return 发布的内容
     */
    @Transactional
    public PublishedContent publishContent(Long userId, String title, String description,
                                           String contentUrl, String thumbnail,
                                           String type, String generationType, String generationConfig,
                                           Long generationRecordId) {
        // 从SiteContext获取当前站点ID
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.SITE_NOT_FOUND);
        }

        if (generationRecordId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "生成记录ID不能为空");
        }
        
        // 查询用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        GenerationRecord record = generationRecordMapper.selectById(generationRecordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权操作此生成记录");
        }
        
        // 创建发布内容对象
        PublishedContent content = new PublishedContent();
        content.setUserId(userId);
        content.setUserName(user.getUsername());
        // 使用用户头像，如果没有则使用默认头像（基于用户ID生成）
        content.setUserAvatarUrl(user.getAvatarUrl() != null ? user.getAvatarUrl() : 
                "https://api.dicebear.com/7.x/avataaars/svg?seed=" + userId);
        content.setTitle(title);
        content.setDescription(description);
        content.setContentUrl(contentUrl);
        
        // 处理缩略图逻辑：如果缩略图为空或与内容URL相同，尝试自动生成
        if (thumbnail == null || thumbnail.isEmpty() || thumbnail.equals(contentUrl)) {
            content.setThumbnail(generateThumbnailUrl(contentUrl, type));
            // 如果生成的缩略图还是和contentUrl一样（非OSS链接），且有传入thumbnail，则使用传入的thumbnail
            if (content.getThumbnail() != null && content.getThumbnail().equals(contentUrl) && thumbnail != null && !thumbnail.isEmpty()) {
                content.setThumbnail(thumbnail);
            }
        } else {
            content.setThumbnail(thumbnail);
        }
        
        content.setType(type);
        content.setGenerationType(generationType);
        content.setGenerationConfig(generationConfig);
        content.setStatus("published"); // 默认状态为展示中
        content.setIsPinned(false); // 默认不置顶
        content.setLikeCount(0); // 默认点赞数为0
        content.setPublishedAt(LocalDateTime.now()); // 设置发布时间
        content.setSiteId(siteId);
        
        // 保存到数据库
        contentMapper.insert(content);

        if (record.getIsPublish() == null || !"1".equals(record.getIsPublish())) {
            record.setIsPublish("1");
            int updated = generationRecordMapper.updateById(record);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "生成记录发布状态更新失败");
            }
        }
        
        // 返回前处理签名URL
        content.setContentUrl(fileStorageService.getFileUrl(content.getContentUrl()));
        content.setThumbnail(fileStorageService.getFileUrl(content.getThumbnail()));
        content.setUserAvatarUrl(fileStorageService.getFileUrl(content.getUserAvatarUrl()));
        
        return content;
    }
    
    /**
     * 获取发布内容列表（支持分页、类型筛选和点赞状态）
     * 
     * @param page 分页参数
     * @param type 类型筛选
     * @param userId 当前用户ID（可选，用于检查点赞状态）
     * @return 分页发布内容列表
     */
    public IPage<PublishedContent> getPublishedContents(Page<PublishedContent> page, String type, Long userId) {
        LambdaQueryWrapper<PublishedContent> wrapper = new LambdaQueryWrapper<>();
        
        // 只查询已发布的内容
        wrapper.eq(PublishedContent::getStatus, "published");
        
        // 筛选类型
        if (StringUtils.hasText(type) && !"all".equals(type)) {
            wrapper.eq(PublishedContent::getType, type);
        }
        
        // 按置顶优先、发布时间倒序排列
        wrapper.orderByDesc(PublishedContent::getIsPinned);
        wrapper.orderByDesc(PublishedContent::getPublishedAt);
        
        IPage<PublishedContent> result = contentMapper.selectPage(page, wrapper);
        
        // 如果有用户ID且结果不为空，批量获取点赞状态
        if (userId != null && !result.getRecords().isEmpty()) {
            List<Long> contentIds = result.getRecords().stream()
                    .map(PublishedContent::getId)
                    .collect(Collectors.toList());
            
            Set<Long> likedIds = likeService.getLikedContentIds(userId, contentIds);
            
            // 设置isLiked状态
            result.getRecords().parallelStream().forEach(item -> {
                item.setIsLiked(likedIds.contains(item.getId()));
                
                // 修复缺少缩略图或缩略图与原图相同的问题
                if (item.getThumbnail() == null || item.getThumbnail().isEmpty() || item.getThumbnail().equals(item.getContentUrl())) {
                    String newThumb = generateThumbnailUrl(item.getContentUrl(), item.getType());
                    if (newThumb != null && !newThumb.equals(item.getContentUrl())) {
                        item.setThumbnail(newThumb);
                    }
                }

                // 处理签名URL
                item.setContentUrl(fileStorageService.getFileUrl(item.getContentUrl()));
                item.setThumbnail(fileStorageService.getFileUrl(item.getThumbnail()));
                item.setUserAvatarUrl(fileStorageService.getFileUrl(item.getUserAvatarUrl()));
            });
        } else if (!result.getRecords().isEmpty()) {
             // 没登录，但要处理签名URL
             result.getRecords().parallelStream().forEach(item -> {
                 item.setIsLiked(false);
                 
                 // 修复缺少缩略图或缩略图与原图相同的问题
                 if (item.getThumbnail() == null || item.getThumbnail().isEmpty() || item.getThumbnail().equals(item.getContentUrl())) {
                     String newThumb = generateThumbnailUrl(item.getContentUrl(), item.getType());
                     if (newThumb != null && !newThumb.equals(item.getContentUrl())) {
                         item.setThumbnail(newThumb);
                     }
                 }

                 // 处理签名URL
                 item.setContentUrl(fileStorageService.getFileUrl(item.getContentUrl()));
                 item.setThumbnail(fileStorageService.getFileUrl(item.getThumbnail()));
                 item.setUserAvatarUrl(fileStorageService.getFileUrl(item.getUserAvatarUrl()));
             });
        }
        
        return result;
    }

    /**
     * 获取发布内容列表（旧方法，保留兼容性，建议使用分页版）
     * 
     * @param type 类型筛选
     * @return 发布内容列表
     */
    public List<PublishedContent> getPublishedContents(String type) {
        return getPublishedContents(new Page<>(1, 100), type, null).getRecords();
    }
    
    /**
     * 根据ID获取发布内容详情
     * 
     * @param id 发布内容ID
     * @return 发布内容
     */
    public PublishedContent getPublishedContentById(Long id) {
        PublishedContent content = contentMapper.selectById(id);
        if (content == null) {
            throw new RuntimeException("发布内容不存在");
        }
        // 只返回已发布的内容
        if (!"published".equals(content.getStatus())) {
            throw new RuntimeException("发布内容不存在或已下架");
        }
        
        // 处理签名URL
        content.setContentUrl(fileStorageService.getFileUrl(content.getContentUrl()));
        content.setThumbnail(fileStorageService.getFileUrl(content.getThumbnail()));
        content.setUserAvatarUrl(fileStorageService.getFileUrl(content.getUserAvatarUrl()));
        
        return content;
    }
    
    /**
     * 切换状态（上架/下架）
     * 
     * @param contentId 发布内容ID
     * @return 更新后的发布内容
     */
    @Transactional
    public PublishedContent toggleStatus(Long contentId) {
        PublishedContent content = contentMapper.selectById(contentId);
        if (content == null) {
            throw new RuntimeException("发布内容不存在");
        }
        
        // 切换状态
        if ("published".equals(content.getStatus())) {
            content.setStatus("hidden");
        } else {
            content.setStatus("published");
        }
        
        contentMapper.updateById(content);
        return content;
    }
    
    /**
     * 切换置顶状态
     * 
     * @param contentId 发布内容ID
     * @return 更新后的发布内容
     */
    @Transactional
    public PublishedContent togglePin(Long contentId) {
        PublishedContent content = contentMapper.selectById(contentId);
        if (content == null) {
            throw new RuntimeException("发布内容不存在");
        }
        
        // 切换置顶状态
        content.setIsPinned(!content.getIsPinned());
        
        contentMapper.updateById(content);
        return content;
    }
    
    /**
     * 删除发布内容（逻辑删除，仅限发布者）
     * 
     * @param userId 用户ID
     * @param contentId 发布内容ID
     */
    @Transactional
    public void deleteContent(Long userId, Long contentId) {
        PublishedContent content = contentMapper.selectById(contentId);
        if (content == null) {
            throw new RuntimeException("发布内容不存在");
        }
        if (!content.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此发布内容");
        }
        
        // 逻辑删除
        contentMapper.deleteById(contentId);
    }
}
