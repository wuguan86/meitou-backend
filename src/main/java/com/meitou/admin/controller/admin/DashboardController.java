package com.meitou.admin.controller.admin;

import com.meitou.admin.common.Result;
import com.meitou.admin.service.admin.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端数据概览控制器
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    
    private final DashboardService dashboardService;
    
    /**
     * 获取统计数据
     * 
     * @param siteId 站点ID（可选）
     * @param timeRange 时间范围
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> stats = dashboardService.getStats(siteId, timeRange, startDate, endDate);
        return Result.success(stats);
    }
    
    /**
     * 获取趋势数据
     * 
     * @param siteId 站点ID（可选）
     * @param timeRange 时间范围
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 趋势数据
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getTrend(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> trend = dashboardService.getTrend(siteId, timeRange, startDate, endDate);
        return Result.success(trend);
    }
    
    /**
     * 获取排名数据
     * 
     * @param siteId 站点ID（可选）
     * @param timeRange 时间范围
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 排名数据
     */
    @GetMapping("/ranking")
    public Result<Map<String, Object>> getRanking(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> ranking = dashboardService.getRanking(siteId, timeRange, startDate, endDate);
        return Result.success(ranking);
    }
}

