package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.GenerationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 生成记录 Mapper 接口
 */
@Mapper
public interface GenerationRecordMapper extends BaseMapper<GenerationRecord> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM generation_records
            WHERE deleted = 0
              AND status = 'processing'
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<GenerationRecord> selectProcessingIgnoreTenant(@Param("limit") int limit);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM generation_records
            WHERE deleted = 0
              AND status = 'processing'
              AND created_at < #{threshold}
            ORDER BY created_at ASC
            LIMIT #{limit}
            """)
    List<GenerationRecord> selectProcessingBeforeIgnoreTenant(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}

