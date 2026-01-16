package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.Character;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper 接口
 */
@Mapper
public interface CharacterMapper extends BaseMapper<Character> {
}
