package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.meitou.admin.entity.UserTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 用户余额流水 Mapper 接口
 */
@Mapper
public interface UserTransactionMapper extends BaseMapper<UserTransaction> {

    /**
     * 忽略多租户插件查询Map列表
     */
    @Select("SELECT ${ew.sqlSelect} FROM user_transactions ${ew.customSqlSegment}")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectMapsIgnoreTenant(@Param(Constants.WRAPPER) Wrapper<UserTransaction> queryWrapper);
}
