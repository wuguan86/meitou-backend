package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PublishedContent;
import com.meitou.admin.service.admin.SquareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/square")
@RequiredArgsConstructor
public class SquareController {

    private final SquareService squareService;

    @GetMapping("/list")
    @SiteScope
    public Result<IPage<PublishedContent>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam Long siteId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        Page<PublishedContent> pageParam = new Page<>(page, size);
        return Result.success(squareService.getPage(pageParam, siteId, type, keyword));
    }

    @PutMapping("/{id}/status")
    @SiteScope
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Long siteId) {
        squareService.toggleStatus(id);
        return Result.success();
    }

    @PutMapping("/{id}/pin")
    @SiteScope
    public Result<Void> togglePin(@PathVariable Long id, @RequestParam Long siteId) {
        squareService.togglePin(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @SiteScope
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long siteId) {
        squareService.removeById(id);
        return Result.success();
    }

    @PutMapping("/{id}/like")
    @SiteScope
    public Result<Void> updateLikeCount(@PathVariable Long id, @RequestParam Integer count, @RequestParam Long siteId) {
        squareService.updateLikeCount(id, count);
        return Result.success();
    }
}
