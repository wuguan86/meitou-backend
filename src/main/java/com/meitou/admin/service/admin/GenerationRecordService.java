package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.storage.FileStorageService;
import com.meitou.admin.util.TitleUtil;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 管理端生成记录服务类
 */
@Service
@RequiredArgsConstructor
public class GenerationRecordService extends ServiceImpl<GenerationRecordMapper, GenerationRecord> {
    
    private final GenerationRecordMapper recordMapper;
    private final FileStorageService fileStorageService;
    
    /**
     * 获取生成记录列表（按站点ID）
     * 注意：调用此方法前，需要先设置 SiteContext.setSiteId(siteId)，
     * 这样多租户插件会自动添加 site_id 过滤条件
     * 
     * @param siteId 站点ID
     * @return 记录列表
     */
    public List<GenerationRecord> getRecordsBySiteId(Long siteId) {
        // 不在这里添加 siteId 条件，因为多租户插件会自动添加
        // 如果在这里添加，会导致 SQL 中出现重复的 site_id 条件
        LambdaQueryWrapper<GenerationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(GenerationRecord::getCreatedAt);
        List<GenerationRecord> records = recordMapper.selectList(wrapper);
        if (records != null) {
            for (GenerationRecord record : records) {
                applySignedMediaUrls(record);
                record.setTitle(TitleUtil.generateTitle(record.getPrompt()));
            }
        }
        return records;
    }
    
    /**
     * 根据ID获取记录
     * 
     * @param id 记录ID
     * @return 记录
     */
    public GenerationRecord getRecordById(Long id) {
        GenerationRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        applySignedMediaUrls(record);
        record.setTitle(TitleUtil.generateTitle(record.getPrompt()));
        return record;
    }

    private void applySignedMediaUrls(GenerationRecord record) {
        if (record == null) {
            return;
        }

        String url = record.getContentUrl();
        String thumb = record.getThumbnailUrl();

        if (StringUtils.hasText(url)) {
            // Check if thumbnail needs generation (null, empty, or same as content URL)
            boolean needGenThumb = !StringUtils.hasText(thumb) || thumb.equals(url);
            
            if (needGenThumb) {
                if (isVideoRecord(record)) {
                    if (url.contains("aliyuncs.com") && !url.contains("?")) {
                        record.setThumbnailUrl(url + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast");
                    } else if ((url.contains("myqcloud.com") || url.contains("cos")) && !url.contains("?")) {
                        record.setThumbnailUrl(url + "?ci-process=snapshot&time=1&format=jpg");
                    }
                } else {
                    // Image record
                    if (url.contains("aliyuncs.com") && !url.contains("?")) {
                        record.setThumbnailUrl(url + "?x-oss-process=image/resize,w_300");
                    } else if ((url.contains("myqcloud.com") || url.contains("cos")) && !url.contains("?")) {
                        record.setThumbnailUrl(url + "?imageMogr2/thumbnail/300x");
                    }
                }
            }
        }

        record.setContentUrl(fileStorageService.getFileUrl(record.getContentUrl()));
        record.setThumbnailUrl(fileStorageService.getFileUrl(record.getThumbnailUrl()));
    }

    private boolean isVideoRecord(GenerationRecord record) {
        String fileType = record.getFileType();
        String type = record.getType();
        return "video".equals(fileType)
                || "video".equals(type)
                || "txt2video".equals(type)
                || "img2video".equals(type);
    }
    
}

