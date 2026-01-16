package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.Like;
import org.apache.ibatis.annotations.Mapper;

/**
 * 点赞 Mapper 接口
 */
@Mapper
public interface LikeMapper extends BaseMapper<Like> {
}
