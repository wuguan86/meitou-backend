package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.PublishedContent;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.PublishedContentMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SquareService extends ServiceImpl<PublishedContentMapper, PublishedContent> {

    private final FileStorageService fileStorageService;

    public IPage<PublishedContent> getPage(Page<PublishedContent> page, Long siteId, String type, String keyword) {
        LambdaQueryWrapper<PublishedContent> wrapper = new LambdaQueryWrapper<>();
        
        // siteId 过滤由 MyBatis Plus 多租户插件自动处理，这里不需要手动添加
        // if (siteId != null) {
        //    wrapper.eq(PublishedContent::getSiteId, siteId);
        // }

        if (StringUtils.hasText(type) && !"all".equals(type)) {
            wrapper.eq(PublishedContent::getType, type);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PublishedContent::getTitle, keyword)
                    .or()
                    .like(PublishedContent::getUserName, keyword));
        }

        wrapper.orderByDesc(PublishedContent::getIsPinned);
        wrapper.orderByDesc(PublishedContent::getPublishedAt);

        IPage<PublishedContent> result = this.page(page, wrapper);
        
        if (result.getRecords() != null) {
            result.getRecords().forEach(item -> {
                item.setContentUrl(fileStorageService.getFileUrl(item.getContentUrl()));
                item.setThumbnail(fileStorageService.getFileUrl(item.getThumbnail()));
                item.setUserAvatarUrl(fileStorageService.getFileUrl(item.getUserAvatarUrl()));
            });
        }
        
        return result;
    }

    @Transactional
    public void toggleStatus(Long id) {
        PublishedContent content = this.getById(id);
        if (content == null) {
            throw new BusinessException(ErrorCode.CONTENT_NOT_FOUND);
        }
        // Toggle between 'published' and 'hidden'
        String newStatus = "published".equals(content.getStatus()) ? "hidden" : "published";
        content.setStatus(newStatus);
        this.updateById(content);
    }

    @Transactional
    public void togglePin(Long id) {
        PublishedContent content = this.getById(id);
        if (content == null) {
            throw new BusinessException(ErrorCode.CONTENT_NOT_FOUND);
        }
        // Handle null isPinned
        boolean currentPin = Boolean.TRUE.equals(content.getIsPinned());
        content.setIsPinned(!currentPin);
        this.updateById(content);
    }

    @Transactional
    public void updateLikeCount(Long id, Integer count) {
        PublishedContent content = this.getById(id);
        if (content == null) {
            throw new BusinessException(ErrorCode.CONTENT_NOT_FOUND);
        }
        content.setLikeCount(count);
        this.updateById(content);
    }
}
