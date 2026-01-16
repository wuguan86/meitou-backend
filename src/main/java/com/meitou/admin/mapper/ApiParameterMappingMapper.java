package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.ApiParameterMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ApiParameterMappingMapper extends BaseMapper<ApiParameterMapping> {
    /**
     * 查询所有参数映射（忽略租户限制）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM api_parameter_mappings WHERE deleted = 0")
    List<ApiParameterMapping> selectAllIgnoreTenant();
}