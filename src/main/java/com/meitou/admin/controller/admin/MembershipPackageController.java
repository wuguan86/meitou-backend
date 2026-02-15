package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.MembershipPackage;
import com.meitou.admin.service.admin.MembershipPackageAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员套餐配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/membership-packages")
@RequiredArgsConstructor
public class MembershipPackageController {

    private final MembershipPackageAdminService membershipPackageService;

    /**
     * 获取所有会员套餐（按站点ID）
     * 
     * @param siteId 站点ID（必传）
     * @return 套餐列表
     */
    @GetMapping
    @SiteScope
    public Result<List<MembershipPackage>> getAllPackages(@RequestParam(required = true) Long siteId) {
        try {
            List<MembershipPackage> packages = membershipPackageService.getPackagesBySiteId(siteId);
            return Result.success("查询成功", packages);
        } catch (Exception e) {
            log.error("获取会员套餐列表失败", e);
            return Result.error("获取套餐列表失败：" + e.getMessage());
        }
    }

    /**
     * 根据ID获取会员套餐
     * 
     * @param id 套餐ID
     * @param siteId 站点ID（必传）
     * @return 套餐信息
     */
    @GetMapping("/{id}")
    @SiteScope
    public Result<MembershipPackage> getPackageById(
            @PathVariable Integer id,
            @RequestParam(required = true) Long siteId) {
        try {
            MembershipPackage pkg = membershipPackageService.getPackageById(id);
            if (pkg != null && !pkg.getSiteId().equals(siteId)) {
                return Result.error("套餐不属于该站点");
            }
            return Result.success("查询成功", pkg);
        } catch (Exception e) {
            log.error("获取会员套餐失败", e);
            return Result.error("获取套餐失败：" + e.getMessage());
        }
    }

    /**
     * 创建会员套餐
     * 
     * @param siteId 站点ID（必传）
     * @param pkg 套餐信息
     * @return 创建的套餐
     */
    @PostMapping
    @SiteScope
    public Result<MembershipPackage> createPackage(
            @RequestParam(required = true) Long siteId,
            @RequestBody MembershipPackage pkg) {
        try {
            pkg.setSiteId(siteId);
            MembershipPackage created = membershipPackageService.createPackage(pkg);
            return Result.success("创建成功", created);
        } catch (Exception e) {
            log.error("创建会员套餐失败", e);
            return Result.error("创建套餐失败：" + e.getMessage());
        }
    }

    /**
     * 更新会员套餐
     * 
     * @param id 套餐ID
     * @param siteId 站点ID（必传）
     * @param pkg 套餐信息
     * @return 更新后的套餐
     */
    @PutMapping("/{id}")
    @SiteScope
    public Result<MembershipPackage> updatePackage(
            @PathVariable Integer id,
            @RequestParam(required = true) Long siteId,
            @RequestBody MembershipPackage pkg) {
        try {
            // 简单的权限校验
            MembershipPackage existing = membershipPackageService.getPackageById(id);
            if (existing != null && !existing.getSiteId().equals(siteId)) {
                return Result.error("套餐不属于该站点");
            }
            
            MembershipPackage updated = membershipPackageService.updatePackage(id, pkg);
            return Result.success("更新成功", updated);
        } catch (Exception e) {
            log.error("更新会员套餐失败", e);
            return Result.error("更新套餐失败：" + e.getMessage());
        }
    }

    /**
     * 删除会员套餐
     * 
     * @param id 套餐ID
     * @param siteId 站点ID（必传）
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @SiteScope
    public Result<Void> deletePackage(
            @PathVariable Integer id,
            @RequestParam(required = true) Long siteId) {
        try {
            MembershipPackage existing = membershipPackageService.getPackageById(id);
            if (existing != null && !existing.getSiteId().equals(siteId)) {
                return Result.error("套餐不属于该站点");
            }
            
            membershipPackageService.deletePackage(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除会员套餐失败", e);
            return Result.error("删除套餐失败：" + e.getMessage());
        }
    }
}
