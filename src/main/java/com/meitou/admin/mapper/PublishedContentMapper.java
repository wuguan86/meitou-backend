package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.meitou.admin.entity.PublishedContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发布内容 Mapper 接口
 */
@Mapper
public interface PublishedContentMapper extends BaseMapper<PublishedContent> {

//    /**
//     * 管理后台分页查询，忽略多租户插件
//     * 用于管理后台查询（可以查询所有站点或指定站点）
//     */
//    @InterceptorIgnore(tenantLine = "true")
//    @Select("SELECT * FROM published_contents ${ew.customSqlSegment}")
//    <E extends IPage<PublishedContent>> E selectAdminPage(E page, @Param(Constants.WRAPPER) Wrapper<PublishedContent> queryWrapper);
}
