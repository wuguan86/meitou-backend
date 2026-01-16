package com.meitou.admin.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meitou.admin.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 充值订单 Mapper 接口
 */
@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    /**
     * 根据订单号查询订单（忽略多租户过滤）
     * 用于支付回调等无法获取上下文的场景
     * 
     * @param orderNo 订单号
     * @return 订单
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM recharge_orders WHERE order_no = #{orderNo} AND deleted = 0 LIMIT 1")
    RechargeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 原子性更新订单为已支付状态（幂等性保证）
     * 只有当订单状态不是 'paid' 时才会更新成功
     * 
     * @param order 订单对象（包含要更新的字段）
     * @return 更新的行数（0表示订单已支付或不存在，1表示更新成功）
     */
    @InterceptorIgnore(tenantLine = "true")
    @org.apache.ibatis.annotations.Update("UPDATE recharge_orders SET " +
            "status = #{status}, " +
            "third_party_order_no = #{thirdPartyOrderNo}, " +
            "callback_info = #{callbackInfo}, " +
            "paid_at = #{paidAt}, " +
            "completed_at = #{completedAt}, " +
            "updated_at = #{updatedAt} " +
            "WHERE id = #{id} " +
            "AND status != 'paid' " + // 关键：只有非paid状态才能更新
            "AND deleted = 0")
    int updateToPaidIfNotPaid(RechargeOrder order);
}
