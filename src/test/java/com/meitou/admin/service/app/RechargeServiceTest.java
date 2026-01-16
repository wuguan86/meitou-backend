package com.meitou.admin.service.app;

import com.meitou.admin.dto.app.RechargeConfigResponse;
import com.meitou.admin.dto.app.RechargeOrderRequest;
import com.meitou.admin.dto.app.RechargeOrderResponse;
import com.meitou.admin.entity.*;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.mapper.*;
import com.meitou.admin.util.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RechargeService 单元测试
 * 重点测试：支付回调幂等性、订单创建频率限制
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("充值服务单元测试")
class RechargeServiceTest {

        @Mock
        private RechargeOrderMapper rechargeOrderMapper;

        @Mock
        private UserMapper userMapper;

        @Mock
        private UserTransactionMapper userTransactionMapper;

        @Mock
        private PaymentConfigMapper paymentConfigMapper;

        @Mock
        private RechargeConfigService rechargeConfigService;

        @Mock
        private PaymentService paymentService;

        @Spy
        private RateLimiter rateLimiter = new RateLimiter();

        @InjectMocks
        private RechargeService rechargeService;

        private RechargeOrder testOrder;
        private User testUser;
        private RechargeConfigResponse testConfig;

        @BeforeEach
        void setUp() {
                // 准备测试数据
                testUser = new User();
                testUser.setId(1L);
                testUser.setUsername("testuser");
                testUser.setBalance(1000);

                testOrder = new RechargeOrder();
                testOrder.setId(1L);
                testOrder.setOrderNo("R1234567890");
                testOrder.setUserId(1L);
                testOrder.setAmount(new BigDecimal("100.00"));
                testOrder.setPoints(10000);
                testOrder.setPaymentType("alipay");
                testOrder.setStatus("paying");
                testOrder.setCreatedAt(LocalDateTime.now());
                testOrder.setSiteId(1L);

                testConfig = new RechargeConfigResponse();
                testConfig.setExchangeRate(100);
                testConfig.setMinAmount(10);
        }

        @Test
        @DisplayName("测试支付回调幂等性 - 并发场景")
        void testPaymentCallbackIdempotency_Concurrent() throws InterruptedException {
                // 模拟10个并发回调请求
                int concurrentRequests = 10;
                ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);

                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger balanceUpdateCount = new AtomicInteger(0);
                AtomicInteger transactionInsertCount = new AtomicInteger(0);

                // 准备回调数据
                Map<String, String> callbackData = new HashMap<>();
                callbackData.put("out_trade_no", testOrder.getOrderNo());
                callbackData.put("trade_status", "TRADE_SUCCESS");
                callbackData.put("total_amount", "100.00");
                callbackData.put("trade_no", "ALIPAY123456");

                // Mock 数据库操作：每次查询返回一个新对象副本，模拟真实数据库行为
                when(rechargeOrderMapper.selectByOrderNo(testOrder.getOrderNo()))
                                .thenAnswer(invocation -> {
                                        RechargeOrder order = new RechargeOrder();
                                        order.setId(testOrder.getId());
                                        order.setOrderNo(testOrder.getOrderNo());
                                        order.setUserId(testOrder.getUserId());
                                        order.setAmount(testOrder.getAmount());
                                        order.setPoints(testOrder.getPoints());
                                        order.setStatus(testOrder.getStatus()); // 应该是初始状态 "paying"
                                        order.setSiteId(testOrder.getSiteId());
                                        return order;
                                });

                when(paymentConfigMapper.selectOne(any()))
                                .thenReturn(createMockPaymentConfig());

                when(paymentService.verifyAlipayCallback(any(), any()))
                                .thenReturn(true);

                // 使用 AtomicBoolean 确保只有一个线程能成功更新状态
                AtomicBoolean alreadyPaid = new AtomicBoolean(false);
                when(rechargeOrderMapper.updateToPaidIfNotPaid(any(RechargeOrder.class)))
                                .thenAnswer(invocation -> {
                                        if (alreadyPaid.compareAndSet(false, true)) {
                                                testOrder.setStatus("paid"); // 更新共享状态以备后用
                                                return 1;
                                        }
                                        return 0;
                                });

                when(userMapper.selectById(testUser.getId()))
                                .thenReturn(testUser);

                when(userMapper.incrementBalance(anyLong(), anyInt(), any(java.time.LocalDateTime.class)))
                                .thenAnswer(invocation -> {
                                        balanceUpdateCount.incrementAndGet();
                                        return 1;
                                });

                when(userTransactionMapper.insert(any(UserTransaction.class)))
                                .thenAnswer(invocation -> {
                                        transactionInsertCount.incrementAndGet();
                                        return 1;
                                });

                // 启动并发请求
                for (int i = 0; i < concurrentRequests; i++) {
                        executor.submit(() -> {
                                try {
                                        startLatch.await(); // 等待统一开始
                                        boolean result = rechargeService.handlePaymentCallback("alipay", callbackData);
                                        if (result) {
                                                successCount.incrementAndGet();
                                        }
                                } catch (Exception e) {
                                        e.printStackTrace();
                                } finally {
                                        doneLatch.countDown();
                                }
                        });
                }

                // 统一开始
                startLatch.countDown();

                // 等待所有线程完成
                boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
                executor.shutdown();

                // 验证结果
                assertTrue(finished, "所有并发请求应该在5秒内完成");

                // 所有请求都应该返回成功（幂等）
                assertEquals(concurrentRequests, successCount.get(),
                                "所有并发请求都应该返回成功");

                // 关键断言：余额只更新1次
                assertEquals(1, balanceUpdateCount.get(),
                                "【幂等性验证】用户余额应该只更新1次，而不是" + balanceUpdateCount.get() + "次");

                // 关键断言：交易记录只插入1次
                assertEquals(1, transactionInsertCount.get(),
                                "【幂等性验证】交易记录应该只插入1次，而不是" + transactionInsertCount.get() + "次");

                // 验证 updateToPaidIfNotPaid 被调用过，具体次数取决于并发调度
                verify(rechargeOrderMapper, atLeastOnce())
                                .updateToPaidIfNotPaid(any(RechargeOrder.class));
        }

        @Test
        @DisplayName("测试订单创建频率限制 - 1秒内多次请求")
        void testCreateOrderRateLimit_MultipleRequestsInOneSecond() throws InterruptedException {
                // 准备测试数据
                RechargeOrderRequest request = new RechargeOrderRequest();
                request.setAmount(new BigDecimal("100.00"));
                request.setPaymentType("alipay");

                // Mock 依赖
                when(userMapper.selectById(testUser.getId()))
                                .thenReturn(testUser);

                when(rechargeConfigService.getActiveConfig())
                                .thenReturn(testConfig);

                when(paymentConfigMapper.selectOne(any()))
                                .thenReturn(createMockPaymentConfig());

                when(paymentService.createAlipayPayment(any(), any(), any(), any(), any()))
                                .thenReturn(createMockPaymentParams());

                when(rechargeOrderMapper.insert(any(RechargeOrder.class)))
                                .thenReturn(1);

                when(rechargeOrderMapper.updateById(any(RechargeOrder.class)))
                                .thenReturn(1);

                // 测试：1秒内连续创建4个订单
                List<Exception> exceptions = new ArrayList<>();
                List<RechargeOrderResponse> successfulOrders = new ArrayList<>();

                for (int i = 0; i < 4; i++) {
                        try {
                                RechargeOrderResponse response = rechargeService.createOrder(
                                                testUser.getId(), request, "test-user-agent");
                                successfulOrders.add(response);
                        } catch (BusinessException e) {
                                exceptions.add(e);
                        }

                        // 间隔很短，模拟快速连续请求
                        if (i < 3) {
                                Thread.sleep(100); // 0.1秒
                        }
                }

                // 验证结果：前3个应该成功，第4个应该被限流
                assertEquals(3, successfulOrders.size(),
                                "【频率限制验证】1分钟内应该只允许创建3个订单");

                assertEquals(1, exceptions.size(),
                                "【频率限制验证】第4个请求应该被限流并抛出异常");

                assertTrue(exceptions.get(0).getMessage().contains("操作过于频繁"),
                                "异常消息应该提示操作频繁");
        }

        @Test
        @DisplayName("测试订单创建频率限制 - 并发场景")
        void testCreateOrderRateLimit_Concurrent() throws InterruptedException, ExecutionException, TimeoutException {
                // 准备测试数据
                RechargeOrderRequest request = new RechargeOrderRequest();
                request.setAmount(new BigDecimal("100.00"));
                request.setPaymentType("alipay");

                // Mock 依赖
                when(userMapper.selectById(testUser.getId()))
                                .thenReturn(testUser);

                when(rechargeConfigService.getActiveConfig())
                                .thenReturn(testConfig);

                when(paymentConfigMapper.selectOne(any()))
                                .thenReturn(createMockPaymentConfig());

                when(paymentService.createAlipayPayment(any(), any(), any(), any(), any()))
                                .thenReturn(createMockPaymentParams());

                when(rechargeOrderMapper.insert(any(RechargeOrder.class)))
                                .thenReturn(1);

                when(rechargeOrderMapper.updateById(any(RechargeOrder.class)))
                                .thenReturn(1);

                // 模拟10个并发请求
                int concurrentRequests = 10;
                ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
                CountDownLatch startLatch = new CountDownLatch(1);

                List<Future<Boolean>> futures = new ArrayList<>();

                for (int i = 0; i < concurrentRequests; i++) {
                        Future<Boolean> future = executor.submit(() -> {
                                try {
                                        startLatch.await();
                                        rechargeService.createOrder(testUser.getId(), request, "test-user-agent");
                                        return true;
                                } catch (BusinessException e) {
                                        // 被限流
                                        return false;
                                } catch (Exception e) {
                                        throw new RuntimeException(e);
                                }
                        });
                        futures.add(future);
                }

                // 统一开始
                startLatch.countDown();

                // 收集结果
                int successCount = 0;
                int blockedCount = 0;

                for (Future<Boolean> future : futures) {
                        Boolean success = future.get(5, TimeUnit.SECONDS);
                        if (success) {
                                successCount++;
                        } else {
                                blockedCount++;
                        }
                }

                executor.shutdown();

                // 验证：只应该有3个成功，其余7个被限流
                assertEquals(3, successCount,
                                "【并发频率限制验证】只应该有3个订单创建成功");

                assertEquals(7, blockedCount,
                                "【并发频率限制验证】应该有7个请求被限流");
        }

        @Test
        @DisplayName("测试金额精度计算 - 小数金额")
        void testAmountPrecisionCalculation() {
                // 准备测试数据 - 小数金额
                RechargeOrderRequest request = new RechargeOrderRequest();
                request.setAmount(new BigDecimal("10.50")); // 10.5元
                request.setPaymentType("alipay");

                // Mock 依赖
                when(userMapper.selectById(testUser.getId()))
                                .thenReturn(testUser);

                RechargeConfigResponse config = new RechargeConfigResponse();
                config.setExchangeRate(100); // 1元=100积分
                config.setMinAmount(5);
                when(rechargeConfigService.getActiveConfig())
                                .thenReturn(config);

                when(paymentConfigMapper.selectOne(any()))
                                .thenReturn(createMockPaymentConfig());

                when(paymentService.createAlipayPayment(any(), any(), any(), any(), any()))
                                .thenReturn(createMockPaymentParams());

                when(rechargeOrderMapper.insert(any(RechargeOrder.class)))
                                .thenAnswer(invocation -> {
                                        RechargeOrder order = invocation.getArgument(0);
                                        // 验证积分计算是否正确
                                        assertEquals(1050, order.getPoints(),
                                                        "【金额精度验证】10.5元 × 100 = 1050积分");
                                        return 1;
                                });

                when(rechargeOrderMapper.updateById(any(RechargeOrder.class)))
                                .thenReturn(1);

                // 执行测试
                RechargeOrderResponse response = rechargeService.createOrder(
                                testUser.getId(), request, "test-user-agent");

                // 验证积分计算正确
                assertEquals(1050, response.getPoints(),
                                "【金额精度验证】返回的积分应该是1050");
        }

        // ==================== 辅助方法 ====================

        private PaymentConfig createMockPaymentConfig() {
                PaymentConfig config = new PaymentConfig();
                config.setId(1L);
                config.setPaymentType("alipay");
                config.setIsEnabled(true);
                config.setConfigJson("{\"appId\":\"test\"}");
                config.setSiteId(1L);
                return config;
        }

        private Map<String, String> createMockPaymentParams() {
                Map<String, String> params = new HashMap<>();
                params.put("orderId", "ORDER123456");
                params.put("payUrl", "https://test.alipay.com/pay");
                return params;
        }
}
