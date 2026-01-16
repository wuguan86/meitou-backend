package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.RechargeConfig;
import com.meitou.admin.service.admin.RechargeConfigAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端充值配置管理控制器
 * 处理充值配置的增删改查
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/recharge-configs")
@RequiredArgsConstructor
public class RechargeConfigController {
    
    private final RechargeConfigAdminService rechargeConfigService;
    
    /**
     * 获取所有充值配置（按站点ID）
     * 
     * @param siteId 站点ID（必传）
     * @return 配置列表
     */
    @GetMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<List<RechargeConfig>> getAllConfigs(@RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            // 由于需要查询单个站点的配置，使用 getConfigBySiteId 方法
            RechargeConfig config = rechargeConfigService.getConfigBySiteId(siteId);
            List<RechargeConfig> configs = config != null ? List.of(config) : List.of();
            return Result.success("查询成功", configs);
        } catch (Exception e) {
            log.error("获取充值配置列表失败", e);
            return Result.error("获取配置列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据站点ID获取充值配置
     * 
     * @param siteId 站点ID（必传）
     * @return 配置
     */
    @GetMapping("/by-site")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<RechargeConfig> getConfigBySite(@RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            RechargeConfig config = rechargeConfigService.getConfigBySiteId(siteId);
            if (config == null) {
                return Result.error("未找到该站点的配置");
            }
            return Result.success("查询成功", config);
        } catch (Exception e) {
            log.error("获取充值配置失败", e);
            return Result.error("获取配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据ID获取充值配置
     * 
     * @param id 配置ID
     * @param siteId 站点ID（必传）
     * @return 配置
     */
    @GetMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<RechargeConfig> getConfigById(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            RechargeConfig config = rechargeConfigService.getConfigById(id);
            // 验证配置的siteId是否与请求参数一致
            if (config != null && !config.getSiteId().equals(siteId)) {
                return Result.error("配置不属于该站点");
            }
            return Result.success("查询成功", config);
        } catch (Exception e) {
            log.error("获取充值配置失败", e);
            return Result.error("获取配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 创建充值配置
     * 
     * @param siteId 站点ID（必传）
     * @param config 配置信息
     * @return 创建的配置
     */
    @PostMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<RechargeConfig> createConfig(
            @RequestParam(required = true) Long siteId,
            @Valid @RequestBody RechargeConfig config) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            // 确保配置的siteId与请求参数一致
            config.setSiteId(siteId);
            
            RechargeConfig created = rechargeConfigService.createConfig(config);
            return Result.success("创建成功", created);
        } catch (Exception e) {
            log.error("创建充值配置失败", e);
            return Result.error("创建配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新充值配置
     * 
     * @param id 配置ID
     * @param siteId 站点ID（必传）
     * @param config 配置信息
     * @return 更新后的配置
     */
    @PutMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<RechargeConfig> updateConfig(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId,
            @RequestBody RechargeConfig config) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            // 确保配置的siteId与请求参数一致
            config.setSiteId(siteId);
            
            RechargeConfig updated = rechargeConfigService.updateConfig(id, config);
            return Result.success("更新成功", updated);
        } catch (Exception e) {
            log.error("更新充值配置失败", e);
            return Result.error("更新配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除充值配置（逻辑删除）
     * 
     * @param id 配置ID
     * @param siteId 站点ID（必传）
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<Void> deleteConfig(
            @PathVariable Long id,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        try {
            rechargeConfigService.deleteConfig(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除充值配置失败", e);
            return Result.error("删除配置失败：" + e.getMessage());
        }
    }
}

