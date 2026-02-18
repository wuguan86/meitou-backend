package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PopupConfig;
import com.meitou.admin.service.admin.PopupConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端营销弹窗配置控制器
 */
@RestController
@RequestMapping("/api/admin/marketing/popup")
@RequiredArgsConstructor
public class PopupConfigController {

    private final PopupConfigService popupConfigService;

    /**
     * 获取弹窗配置列表
     * 
     * @param siteId 站点ID
     * @return 弹窗配置列表
     */
    @GetMapping
    @SiteScope
    public Result<List<PopupConfig>> getPopupList(@RequestParam(required = true) Long siteId) {
        List<PopupConfig> list = popupConfigService.getPopupListBySiteId(siteId);
        return Result.success(list);
    }

    /**
     * 创建弹窗配置
     * 
     * @param siteId 站点ID
     * @param config 弹窗配置
     * @return 创建后的配置
     */
    @PostMapping
    @SiteScope
    public Result<PopupConfig> createPopup(
            @RequestParam(required = true) Long siteId,
            @RequestBody PopupConfig config) {
        config.setSiteId(siteId);
        PopupConfig created = popupConfigService.createPopup(config);
        return Result.success("创建成功", created);
    }

    /**
     * 更新弹窗配置
     * 
     * @param id 弹窗ID
     * @param siteId 站点ID
     * @param config 弹窗配置
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    @SiteScope
    public Result<PopupConfig> updatePopup(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestBody PopupConfig config) {
        config.setId(id);
        config.setSiteId(siteId);
        PopupConfig updated = popupConfigService.updatePopup(config);
        return Result.success("更新成功", updated);
    }

    /**
     * 删除弹窗配置
     * 
     * @param id 弹窗ID
     * @param siteId 站点ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    @SiteScope
    public Result<Void> deletePopup(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        popupConfigService.deletePopup(id);
        return Result.success("删除成功");
    }
}
