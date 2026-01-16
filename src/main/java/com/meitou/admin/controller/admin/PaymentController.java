package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PaymentConfig;
import com.meitou.admin.service.admin.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端支付管理控制器
 */
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentConfigService paymentConfigService;
    
    /**
     * 获取支付配置列表（按站点）
     * 
     * @param siteId 站点ID（必传）
     * @return 支付配置列表
     */
    @GetMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<List<PaymentConfig>> getPaymentConfigs(@RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置（虽然这里不使用，但保持一致性）
        List<PaymentConfig> configs = paymentConfigService.getPaymentConfigs(siteId);
        return Result.success(configs);
    }
    
    /**
     * 更新支付配置的启用状态
     * 
     * @param paymentType 支付类型：wechat-微信支付，alipay-支付宝支付，bank_transfer-对公转账
     * @param siteId 站点ID（必传）
     * @param isEnabled 是否启用
     * @return 更新后的支付配置
     */
    @PutMapping("/{paymentType}/status")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<PaymentConfig> updatePaymentConfigStatus(
            @PathVariable String paymentType,
            @RequestParam(required = true) Long siteId,
            @RequestBody Boolean isEnabled) {
        // SiteContext 已由 @SiteScope 注解自动设置
        PaymentConfig updated = paymentConfigService.updatePaymentConfigStatus(paymentType, siteId, isEnabled);
        return Result.success("更新成功", updated);
    }
    
    /**
     * 创建或更新支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID（必传）
     * @param config 支付配置信息
     * @return 创建或更新后的支付配置
     */
    @PostMapping("/{paymentType}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<PaymentConfig> createOrUpdatePaymentConfig(
            @PathVariable String paymentType,
            @RequestParam(required = true) Long siteId,
            @RequestBody PaymentConfig config) {
        // SiteContext 已由 @SiteScope 注解自动设置
        PaymentConfig saved = paymentConfigService.createOrUpdatePaymentConfig(paymentType, siteId, config);
        return Result.success("保存成功", saved);
    }
    
    /**
     * 更新支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID（必传）
     * @param config 支付配置信息
     * @return 更新后的支付配置
     */
    @PutMapping("/{paymentType}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<PaymentConfig> updatePaymentConfig(
            @PathVariable String paymentType,
            @RequestParam(required = true) Long siteId,
            @RequestBody PaymentConfig config) {
        // SiteContext 已由 @SiteScope 注解自动设置
        PaymentConfig updated = paymentConfigService.updatePaymentConfig(paymentType, siteId, config);
        return Result.success("更新成功", updated);
    }
    
    /**
     * 删除支付配置
     * 
     * @param paymentType 支付类型
     * @param siteId 站点ID（必传）
     * @return 删除结果
     */
    @DeleteMapping("/{paymentType}")
    @SiteScope // 使用 AOP 自动处理 SiteContext
    public Result<Void> deletePaymentConfig(
            @PathVariable String paymentType,
            @RequestParam(required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置
        paymentConfigService.deletePaymentConfig(paymentType, siteId);
        return Result.success("删除成功");
    }
}

