package com.meitou.admin.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meitou.admin.dto.admin.RechargeOrderQueryRequest;
import com.meitou.admin.entity.RechargeOrder;
import com.meitou.admin.entity.User;
import com.meitou.admin.mapper.RechargeOrderMapper;
import com.meitou.admin.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端充值订单服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeOrderAdminService extends ServiceImpl<RechargeOrderMapper, RechargeOrder> {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final UserMapper userMapper;

    /**
     * 获取充值订单列表
     */
    public IPage<RechargeOrder> getRechargeOrders(RechargeOrderQueryRequest request) {
        Page<RechargeOrder> page = new Page<>(request.getPage(), request.getSize());
        QueryWrapper<RechargeOrder> wrapper = buildQueryWrapper(request);
        
        wrapper.orderByDesc("created_at");
        
        IPage<RechargeOrder> result = rechargeOrderMapper.selectPage(page, wrapper);
        
        // 填充用户信息
        if (!result.getRecords().isEmpty()) {
            Set<Long> userIds = result.getRecords().stream()
                    .map(RechargeOrder::getUserId)
                    .collect(Collectors.toSet());
            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                // Map users by ID
                var userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
                result.getRecords().forEach(order -> order.setUser(userMap.get(order.getUserId())));
            }
        }
        
        return result;
    }

    /**
     * 获取总充值金额
     */
    public BigDecimal getTotalAmount(RechargeOrderQueryRequest request) {
        QueryWrapper<RechargeOrder> wrapper = buildQueryWrapper(request);
        
        // 只需要查询成功的订单
        wrapper.eq("status", "paid");
        
        wrapper.select("IFNULL(SUM(amount), 0) as total");
        
        Map<String, Object> map = rechargeOrderMapper.selectMaps(wrapper).stream().findFirst().orElse(null);
        if (map != null && map.get("total") != null) {
            return new BigDecimal(map.get("total").toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取导出列表
     */
    public List<RechargeOrder> getExportList(RechargeOrderQueryRequest request) {
        QueryWrapper<RechargeOrder> wrapper = buildQueryWrapper(request);
        wrapper.orderByDesc("created_at");
        // 限制导出数量，防止OOM
        wrapper.last("LIMIT 10000");
        
        List<RechargeOrder> list = rechargeOrderMapper.selectList(wrapper);
        
        // 填充用户信息
        if (!list.isEmpty()) {
            Set<Long> userIds = list.stream()
                    .map(RechargeOrder::getUserId)
                    .collect(Collectors.toSet());
            if (!userIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(userIds);
                var userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
                list.forEach(order -> order.setUser(userMap.get(order.getUserId())));
            }
        }
        return list;
    }

    private QueryWrapper<RechargeOrder> buildQueryWrapper(RechargeOrderQueryRequest request) {
        QueryWrapper<RechargeOrder> wrapper = new QueryWrapper<>();
        
        // 搜索手机号
        if (StringUtils.hasText(request.getSearch())) {
            // 需要先查用户ID
            QueryWrapper<User> userWrapper = new QueryWrapper<>();
            userWrapper.like("phone", request.getSearch());
            List<User> users = userMapper.selectList(userWrapper);
            if (users.isEmpty()) {
                // 没有匹配的用户，设置一个不存在的ID条件
                wrapper.eq("user_id", -1L);
            } else {
                Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
                wrapper.in("user_id", userIds);
            }
        }
        
        // 支付方式
        if (StringUtils.hasText(request.getPaymentType()) && !"全部".equals(request.getPaymentType())) {
            wrapper.eq("payment_type", request.getPaymentType().toLowerCase());
        }
        
        // 日期范围
        if (request.getStartDate() != null) {
            wrapper.ge("created_at", request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.lt("created_at", request.getEndDate().plusDays(1).atStartOfDay());
        }
        
        return wrapper;
    }
}
