package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.UserMembershipPeriod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMembershipPeriodMapper extends BaseMapper<UserMembershipPeriod> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT *
            FROM user_membership_periods
            WHERE deleted = 0
              AND status = 'scheduled'
              AND start_at <= #{threshold}
            ORDER BY start_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<UserMembershipPeriod> selectDueScheduledIgnoreTenant(@Param("threshold") LocalDateTime threshold,
                                                              @Param("limit") int limit);
}
