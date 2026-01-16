package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.InvitationCode;
import com.meitou.admin.service.admin.InvitationCodeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理端邀请码管理控制器
 */
@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
public class InvitationController {
    
    private final InvitationCodeService codeService;
    
    /**
     * 获取邀请码列表（支持分页、搜索）
     * 
     * @param siteId 站点ID
     * @param page 页码
     * @param size 每页大小
     * @param code 邀请码
     * @param channel 渠道
     * @param status 状态
     * @return 邀请码列表
     */
    @GetMapping
    @SiteScope(required = false)
    public Result<IPage<InvitationCode>> getCodes(
            @RequestParam(value = "siteId", required = false) Long siteId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "status", required = false) String status) {
        
        return Result.success(codeService.getPage(new Page<>(page, size), code, channel, status));
    }
    
    /**
     * 生成邀请码
     * 
     * @param request 生成请求
     * @return 生成的邀请码列表
     */
    @PostMapping("/generate")
    public Result<List<InvitationCode>> generateCodes(@RequestBody Map<String, Object> request) {
        Integer count = (Integer) request.get("count");
        Integer points = (Integer) request.get("points");
        Integer maxUses = (Integer) request.get("maxUses");
        Long siteId = request.get("siteId") != null ? Long.valueOf(request.get("siteId").toString()) : null;
        String channel = (String) request.get("channel");
        String validStartDateStr = (String) request.get("validStartDate");
        String validEndDateStr = (String) request.get("validEndDate");
        
        LocalDate validStartDate = validStartDateStr != null ? LocalDate.parse(validStartDateStr) : null;
        LocalDate validEndDate = validEndDateStr != null ? LocalDate.parse(validEndDateStr) : null;
        
        if (siteId == null) {
            return Result.error("站点ID不能为空");
        }
        
        List<InvitationCode> codes = codeService.generateCodes(
                count, points, maxUses, siteId, channel, validStartDate, validEndDate
        );
        return Result.success("生成成功", codes);
    }
    
    /**
     * 更新邀请码
     * 
     * @param id 邀请码ID
     * @param code 邀请码信息
     * @return 更新后的邀请码
     */
    @PutMapping("/{id}")
    public Result<InvitationCode> updateCode(@PathVariable Long id, @RequestBody InvitationCode code) {
        InvitationCode updated = codeService.updateCode(id, code);
        return Result.success("更新成功", updated);
    }

    /**
     * 删除邀请码
     * 
     * @param id 邀请码ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCode(@PathVariable Long id) {
        codeService.deleteCode(id);
        return Result.success("删除成功", null);
    }
}

