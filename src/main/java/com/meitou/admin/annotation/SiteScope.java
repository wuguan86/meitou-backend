package com.meitou.admin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 站点作用域注解
 * 用于自动设置和恢复 SiteContext，简化多租户处理逻辑
 * 
 * 使用示例：
 * <pre>
 * {@code
 * @GetMapping
 * @SiteScope  // 自动从名为 siteId 的参数中获取站点ID
 * public Result<List<MenuConfig>> getMenus(@RequestParam Long siteId) {
 *     // SiteContext 已自动设置，直接使用即可
 *     return Result.success(menuService.getMenusBySiteId(siteId));
 * }
 * 
 * @PutMapping("/{id}")
 * @SiteScope(paramName = "siteId", required = true)  // 指定参数名，并验证必填
 * public Result<MenuConfig> updateMenu(
 *         @PathVariable Long id,
 *         @RequestParam Long siteId,
 *         @RequestBody MenuConfig menu) {
 *     // SiteContext 已自动设置
 *     return Result.success(menuService.updateMenu(id, menu));
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SiteScope {
    
    /**
     * 站点ID参数名，默认为 "siteId"
     * AOP 会从方法参数中查找该名称的参数
     * 
     * @return 参数名
     */
    String paramName() default "siteId";
    
    /**
     * 是否必填，默认为 true
     * 如果为 true，当参数为 null 时会抛出异常
     * 
     * @return 是否必填
     */
    boolean required() default true;
    
    /**
     * 错误提示信息
     * 当 required=true 且参数为 null 时使用的错误信息
     * 
     * @return 错误信息
     */
    String message() default "站点ID不能为空，请选择所属站点（医美类=1，电商类=2，生活服务类=3）";
}

