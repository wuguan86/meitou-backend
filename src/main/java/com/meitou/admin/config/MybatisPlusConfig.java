package com.meitou.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.meitou.admin.common.SiteContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类
 * 配置分页插件和多租户插件
 */
@Configuration
public class MybatisPlusConfig {
    
    /**
     * 配置MyBatis Plus拦截器
     * 包含分页插件和多租户插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加多租户插件（注意：需要先添加多租户插件，再添加分页插件）
        TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor();
        tenantInterceptor.setTenantLineHandler(new SiteTenantLineHandler());
        interceptor.addInnerInterceptor(tenantInterceptor);
        
        // 添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.addInnerInterceptor(paginationInterceptor);
        
        return interceptor;
    }
    
    /**
     * 多租户处理器
     * 实现TenantLineHandler接口，从SiteContext获取站点ID
     */
    public static class SiteTenantLineHandler implements TenantLineHandler {
        
        /**
         * 获取租户ID（站点ID）
         * 
         * @return 站点ID表达式
         */
        @Override
        public Expression getTenantId() {
            Long siteId = SiteContext.getSiteId();
            if (siteId == null) {
                // 如果没有站点ID，返回0，这样查询会失败，避免数据泄露
                return new LongValue(0L);
            }
            return new LongValue(siteId);
        }
        
        /**
         * 获取多租户字段名
         * 
         * @return 字段名
         */
        @Override
        public String getTenantIdColumn() {
            return "site_id";
        }
        
        /**
         * 判断表是否需要多租户过滤
         * 
         * @param tableName 表名
         * @return 是否需要过滤（true表示忽略，false表示需要过滤）
         */
        @Override
        public boolean ignoreTable(String tableName) {
            // 管理后台的表不需要多租户过滤
            if (tableName != null && tableName.startsWith("backend_")) {
                return true;
            }
            // 站点表不需要多租户过滤
            if ("sites".equals(tableName)) {
                return true;
            }
            
            return false;
        }
    }
}

