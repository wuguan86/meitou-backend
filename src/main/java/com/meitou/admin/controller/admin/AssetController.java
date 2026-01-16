package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.UserAsset;
import com.meitou.admin.service.admin.UserAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端资产管理控制器
 */
@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
public class AssetController {

    private final UserAssetService userAssetService;

    /**
     * 获取资产列表
     */
    @GetMapping
    @SiteScope
    public Result<IPage<UserAsset>> list(
            @RequestParam Long siteId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(userAssetService.getAssets(new Page<>(page, size), type, search));
    }

    /**
     * 删除资产
     */
    @DeleteMapping("/{id}")
    @SiteScope
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long siteId) {
        userAssetService.removeById(id);
        return Result.success();
    }
}
