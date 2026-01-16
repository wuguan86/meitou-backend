package com.meitou.admin.config;

import com.meitou.admin.entity.BackendAccount;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 自动填充处理器
 * 自动填充创建时间和更新时间
 */
@Component
public class MetaObjectHandler implements com.baomidou.mybatisplus.core.handlers.MetaObjectHandler {
    
    /**
     * 插入时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        
        // 如果是 BackendAccount 实体，且 siteId 为 null，则自动填充为 0（表示全局账号）
        if (metaObject.getOriginalObject() instanceof BackendAccount) {
            Object siteId = metaObject.getValue("siteId");
            if (siteId == null) {
                this.strictInsertFill(metaObject, "siteId", Long.class, 0L);
            }
        }
    }
    
    /**
     * 更新时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}

