package com.meitou.admin.task;

import com.meitou.admin.entity.RechargeOrder;
import com.meitou.admin.mapper.RechargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 充值订单超时处理任务
 * 自动取消长时间未支付的订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeOrderTimeoutTask {

    private final RechargeOrderMapper rechargeOrderMapper;

    @Value("${recharge.order.timeout.minutes:30}")
    private int timeoutMinutes;

    @Value("${recharge.order.timeout.batchSize:100}")
    private int batchSize;

    @Scheduled(fixedRateString = "${recharge.order.timeout.fixedRateMs:300000}")
    public void cancelTimeoutOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        
        List<RechargeOrder> timeoutOrders = rechargeOrderMapper.selectPendingOrPayingBefore(threshold, batchSize);
        
        if (timeoutOrders.isEmpty()) {
            return;
        }
        
        log.info("发现 {} 个超时未支付订单，开始处理...", timeoutOrders.size());
        
        int successCount = 0;
        for (RechargeOrder order : timeoutOrders) {
            try {
                int rows = rechargeOrderMapper.updateToCancelled(order.getId(), LocalDateTime.now());
                if (rows > 0) {
                    successCount++;
                    log.info("订单已超时自动取消: 订单号={}, 用户ID={}, 创建时间={}", 
                            order.getOrderNo(), order.getUserId(), order.getCreatedAt());
                }
            } catch (Exception e) {
                log.error("取消超时订单失败: 订单号={}", order.getOrderNo(), e);
            }
        }
        
        log.info("超时订单处理完成: 总数={}, 成功={}, 失败={}", 
                timeoutOrders.size(), successCount, timeoutOrders.size() - successCount);
    }
}