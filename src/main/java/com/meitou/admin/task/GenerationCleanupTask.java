package com.meitou.admin.task;

import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.service.app.GenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生成任务清理定时任务
 * 处理长时间卡在 processing 状态的任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationCleanupTask {

    private final GenerationRecordMapper generationRecordMapper;
    private final GenerationService generationService;

    @Value("${generation.task.sync.batchSize:50}")
    private int syncBatchSize;

    @Value("${generation.task.timeout.minutes:60}")
    private int timeoutMinutes;

    @Value("${generation.task.timeout.batchSize:50}")
    private int timeoutBatchSize;

    @Scheduled(fixedRateString = "${generation.task.sync.fixedRateMs:60000}")
    public void syncProcessingTasks() {
        List<GenerationRecord> processingRecords = generationRecordMapper.selectProcessingIgnoreTenant(syncBatchSize);
        if (processingRecords.isEmpty()) {
            return;
        }

        for (GenerationRecord record : processingRecords) {
            if (record.getSiteId() == null) {
                continue;
            }
            runWithSiteContext(record.getSiteId(), () -> {
                try {
                    generationService.getTaskStatus(record.getId());
                } catch (Exception e) {
                    log.warn("同步任务状态失败 ID={}: {}", record.getId(), e.getMessage());
                }
            });
        }
    }

    @Scheduled(fixedRateString = "${generation.task.timeout.fixedRateMs:300000}")
    public void cleanupStuckTasks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        
        List<GenerationRecord> stuckRecords = generationRecordMapper.selectProcessingBeforeIgnoreTenant(threshold, timeoutBatchSize);
        
        if (stuckRecords.isEmpty()) {
            return;
        }
        
        log.info("发现 {} 个卡死的生成任务，开始处理...", stuckRecords.size());
        
        for (GenerationRecord record : stuckRecords) {
            try {
                if (record.getSiteId() == null) {
                    continue;
                }
                runWithSiteContext(record.getSiteId(), () -> processStuckRecord(record));
            } catch (Exception e) {
                log.error("处理卡死任务失败 ID={}: {}", record.getId(), e.getMessage());
            }
        }
    }

    private void processStuckRecord(GenerationRecord record) {
        try {
            generationService.getTaskStatus(record.getId());
        } catch (Exception e) {
            log.warn("处理超时任务前同步状态失败 ID={}: {}", record.getId(), e.getMessage());
        }

        GenerationRecord r = generationRecordMapper.selectById(record.getId());
        if (r != null && "processing".equals(r.getStatus())) {
            log.info("任务超时自动失败退款 ID={}, Cost={}", r.getId(), r.getCost());
            generationService.failIfProcessingAndRefund(r.getId(), "任务执行超时，系统自动退款");
        }
    }

    private void runWithSiteContext(Long siteId, Runnable runnable) {
        Long originalSiteId = SiteContext.getSiteId();
        try {
            SiteContext.setSiteId(siteId);
            runnable.run();
        } finally {
            if (originalSiteId == null) {
                SiteContext.clear();
            } else {
                SiteContext.setSiteId(originalSiteId);
            }
        }
    }
}
