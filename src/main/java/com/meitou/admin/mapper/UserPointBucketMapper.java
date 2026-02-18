package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.UserPointBucket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserPointBucketMapper extends BaseMapper<UserPointBucket> {

    @Select("SELECT COUNT(1) FROM user_point_buckets WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    int countByUserId(@Param("userId") Long userId);


    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM user_point_buckets
            WHERE user_id = #{userId}
              AND deleted = 0
              AND status = 'active'
              AND remaining_points > 0
              AND (expires_at IS NULL OR expires_at > #{now})
              AND site_id = #{siteId}
            ORDER BY (expires_at IS NULL) ASC, expires_at ASC, id ASC
            FOR UPDATE
            """)
    List<UserPointBucket> selectBucketsForDeduct(@Param("userId") Long userId,
                                                 @Param("siteId") Long siteId,
                                                 @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(SUM(remaining_points), 0)
            FROM user_point_buckets
            WHERE user_id = #{userId}
              AND deleted = 0
              AND status = 'active'
              AND remaining_points > 0
              AND (expires_at IS NULL OR expires_at > NOW())
            """)
    int sumAvailablePoints(@Param("userId") Long userId);

    @Update("""
            UPDATE user_point_buckets
            SET remaining_points = 0,
                status = 'expired',
                updated_at = #{updatedAt}
            WHERE id = #{id}
              AND deleted = 0
              AND status = 'active'
            """)
    int expireBucket(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM user_point_buckets
            WHERE deleted = 0
              AND status = 'active'
              AND remaining_points > 0
              AND expires_at IS NOT NULL
              AND expires_at <= #{threshold}
            ORDER BY expires_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<UserPointBucket> selectExpiredBucketsIgnoreTenant(@Param("threshold") LocalDateTime threshold,
                                                           @Param("limit") int limit);
}
