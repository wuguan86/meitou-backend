package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.service.admin.GenerationRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端生成记录控制器
 */
@RestController
@RequestMapping("/api/admin/generation-records")
@RequiredArgsConstructor
public class GenerationRecordController {
    
    private final GenerationRecordService recordService;
    
    /**
     * 获取生成记录列表（按站点ID，分页）
     * 
     * @param siteId 站点ID（必传）：1=医美类，2=电商类，3=生活服务类
     * @param page 页码，默认1
     * @param size 每页数量，默认10
     * @return 记录分页
     */
    @GetMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<Page<GenerationRecord>> getRecords(
            @RequestParam(required = true) Long siteId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        // SiteContext 已由 @SiteScope 注解自动设置
        Page<GenerationRecord> records = recordService.getRecordsBySiteId(siteId, page, size);
        return Result.success(records);
    }
    
    /**
     * 获取生成记录详情
     * 
     * @param id 记录ID
     * @return 记录信息
     */
    @GetMapping("/{id}")
    public Result<GenerationRecord> getRecord(@PathVariable Long id) {
        GenerationRecord record = recordService.getRecordById(id);
        return Result.success(record);
    }
    
}

