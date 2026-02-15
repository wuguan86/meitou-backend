package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.UserPointBucketUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserPointBucketUsageMapper extends BaseMapper<UserPointBucketUsage> {

    @Select("""
            SELECT *
            FROM user_point_bucket_usages
            WHERE user_id = #{userId}
              AND business_type = #{businessType}
              AND business_id = #{businessId}
              AND deleted = 0
            """)
    List<UserPointBucketUsage> selectByBusiness(@Param("userId") Long userId,
                                               @Param("businessType") String businessType,
                                               @Param("businessId") Long businessId);

    @Select("""
            SELECT COALESCE(SUM(points), 0)
            FROM user_point_bucket_usages
            WHERE user_id = #{userId}
              AND business_type = #{businessType}
              AND business_id = #{businessId}
              AND deleted = 0
            """)
    int sumPointsByBusiness(@Param("userId") Long userId,
                            @Param("businessType") String businessType,
                            @Param("businessId") Long businessId);
}
