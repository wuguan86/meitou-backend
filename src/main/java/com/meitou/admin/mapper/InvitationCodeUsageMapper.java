package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.InvitationCodeUsage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邀请码使用记录 Mapper 接口
 */
@Mapper
public interface InvitationCodeUsageMapper extends BaseMapper<InvitationCodeUsage> {
}
