package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.MembershipPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会员套餐配置 Mapper 接口
 */
@Mapper
public interface MembershipPackageMapper extends BaseMapper<MembershipPackage> {

    /**
     * 根据ID查询会员套餐（忽略逻辑删除）
     * 用于查询历史会员权益时，即使套餐已删除也能查到
     */
    @Select("SELECT * FROM membership_packages WHERE id = #{id}")
    MembershipPackage selectByIdIgnoreDeleted(@Param("id") Long id);
}
