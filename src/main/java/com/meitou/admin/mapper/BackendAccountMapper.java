package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.BackendAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台账号 Mapper 接口
 */
@Mapper
public interface BackendAccountMapper extends BaseMapper<BackendAccount> {
}

