package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.meitou.admin.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper 接口
 * 继承 MyBatis Plus 的 BaseMapper，提供基础 CRUD 方法
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 忽略多租户插件查询总数
     */
    @Select("SELECT COUNT(*) FROM users ${ew.customSqlSegment}")
    @InterceptorIgnore(tenantLine = "true")
    Long selectCountIgnoreTenant(@Param(Constants.WRAPPER) Wrapper<User> queryWrapper);

    /**
     * 忽略多租户插件查询Map列表
     */
    @Select("SELECT ${ew.sqlSelect} FROM users ${ew.customSqlSegment}")
    @InterceptorIgnore(tenantLine = "true")
    List<Map<String, Object>> selectMapsIgnoreTenant(@Param(Constants.WRAPPER) Wrapper<User> queryWrapper);

    /**
     * 忽略多租户插件查询列表
     */
    @Select("SELECT * FROM users ${ew.customSqlSegment}")
    @InterceptorIgnore(tenantLine = "true")
    List<User> selectListIgnoreTenant(@Param(Constants.WRAPPER) Wrapper<User> queryWrapper);

    /**
     * 扣减用户余额
     * @param userId 用户ID
     * @param cost 消耗积分
     * @param updatedAt 更新时间（由Java程序生成）
     * @return 更新行数
     */
    @Update("UPDATE users SET balance = balance - #{cost}, updated_at = #{updatedAt} WHERE id = #{userId} AND balance >= #{cost}")
    int deductBalance(@Param("userId") Long userId, @Param("cost") Integer cost, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Update("UPDATE users SET balance = balance + #{delta}, updated_at = #{updatedAt} WHERE id = #{userId}")
    int incrementBalance(@Param("userId") Long userId, @Param("delta") Integer delta, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 根据手机号查询用户（包含已删除的）
     * @param phone 手机号
     * @return 用户
     */
    @Select("SELECT * FROM users WHERE phone = #{phone} LIMIT 1")
    User selectByPhoneIncludeDeleted(@Param("phone") String phone);

    /**
     * 恢复已删除用户并重置信息
     * @param user 用户对象
     * @return 更新行数
     */
    @Update("UPDATE users SET deleted = 0, balance = #{balance}, password = #{password}, email = #{email}, " +
            "username = #{username}, site_id = #{siteId}, created_at = #{createdAt}, updated_at = #{updatedAt}, " +
            "status = #{status}, avatar_url = #{avatarUrl} WHERE id = #{id}")
    int restoreUser(User user);
}

