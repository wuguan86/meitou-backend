package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.PaymentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 支付配置 Mapper 接口
 */
@Mapper
public interface PaymentConfigMapper extends BaseMapper<PaymentConfig> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM payment_configs WHERE payment_type = #{paymentType} AND is_enabled = 1 AND deleted = 0")
    List<PaymentConfig> selectEnabledByPaymentTypeIgnoreTenant(@Param("paymentType") String paymentType);
}

