package com.meitou.admin.aspect;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.common.SiteContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 站点作用域切面类
 * 自动处理 SiteContext 的设置和恢复，避免重复代码
 * 
 * @author Meitou Team
 */
@Slf4j
@Aspect
@Component
@Order(1) // 确保在其他切面之前执行
public class SiteScopeAspect {
    
    /**
     * 环绕通知，处理 @SiteScope 注解
     * 
     * @param joinPoint 连接点
     * @param siteScope 注解信息
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("@annotation(siteScope)")
    public Object around(ProceedingJoinPoint joinPoint, SiteScope siteScope) throws Throwable {
        // 获取方法签名和参数值
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        
        // 查找站点ID参数
        Long siteId = findSiteIdParameter(args, parameters, siteScope.paramName());
        
        // 验证必填参数
        if (siteScope.required() && siteId == null) {
            log.warn("站点ID参数为空，方法：{}", method.getName());
            return Result.error(siteScope.message());
        }
        
        // 如果 siteId 为 null 且不是必填，直接执行方法（不设置 SiteContext）
        if (siteId == null) {
            return joinPoint.proceed();
        }
        
        // 保存原始站点ID
        Long originalSiteId = SiteContext.getSiteId();
        
        try {
            // 设置新的站点ID
            SiteContext.setSiteId(siteId);
            log.debug("设置 SiteContext.siteId = {}, 方法：{}", siteId, method.getName());
            
            // 执行目标方法
            return joinPoint.proceed();
        } finally {
            // 恢复原始站点ID
            if (originalSiteId != null) {
                SiteContext.setSiteId(originalSiteId);
                log.debug("恢复 SiteContext.siteId = {}", originalSiteId);
            } else {
                SiteContext.clear();
                log.debug("清除 SiteContext");
            }
        }
    }
    
    /**
     * 从方法参数中查找站点ID
     * 支持从 @RequestParam 参数或 @RequestBody 对象中获取 siteId
     * 
     * @param args 方法参数值数组
     * @param parameters 方法参数数组
     * @param paramName 参数名
     * @return 站点ID，如果未找到则返回 null
     */
    private Long findSiteIdParameter(Object[] args, Parameter[] parameters, String paramName) {
        if (args == null || parameters == null || args.length != parameters.length) {
            return null;
        }
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            
            // 1. 首先尝试从 @RequestParam 参数中获取（参数类型为 Long）
            if (parameter.getType() == Long.class || parameter.getType() == long.class) {
                // 获取参数的实际名称
                String name = null;
                
                // 优先检查 @RequestParam 注解的 value
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    String value = requestParam.value();
                    if (!value.isEmpty()) {
                        name = value;
                    } else {
                        // 如果 @RequestParam 没有指定 value，使用参数名
                        name = parameter.getName();
                    }
                } else {
                    // 没有 @RequestParam 注解，使用参数名
                    name = parameter.getName();
                }
                
                // 如果参数名匹配，返回该参数值
                if (paramName.equals(name)) {
                    if (arg instanceof Long) {
                        return (Long) arg;
                    } else if (arg instanceof Number) {
                        return ((Number) arg).longValue();
                    }
                }
            }
            
            // 2. 尝试从 @RequestBody 对象中获取 siteId 字段
            // 检查是否有 @RequestBody 注解，且参数对象有 siteId 字段
            if (parameter.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class) != null) {
                Long siteId = extractSiteIdFromObject(arg, paramName);
                if (siteId != null) {
                    return siteId;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从对象中提取 siteId 字段值（通过反射）
     * 
     * @param obj 对象
     * @param fieldName 字段名（默认 "siteId"）
     * @return 站点ID，如果未找到则返回 null
     */
    private Long extractSiteIdFromObject(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        
        try {
            // 尝试获取字段
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (NoSuchFieldException e) {
            // 字段不存在，忽略
        } catch (IllegalAccessException e) {
            log.warn("无法访问字段 {}: {}", fieldName, e.getMessage());
        }
        
        // 如果直接获取字段失败，尝试通过 getter 方法获取
        try {
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            java.lang.reflect.Method getter = obj.getClass().getMethod(getterName);
            Object value = getter.invoke(obj);
            
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            // getter 方法不存在或调用失败，忽略
        }
        
        return null;
    }
}

