package com.meitou.admin.controller.app;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.*;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.service.app.RechargeConfigService;
import com.meitou.admin.service.app.RechargeService;
import com.meitou.admin.util.TokenUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 充值控制器
 * 处理充值相关的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/app/recharge")
@RequiredArgsConstructor
public class RechargeController {
    
    private final RechargeService rechargeService;
    private final RechargeConfigService rechargeConfigService;
    
    /**
     * 获取充值配置
     * 多租户插件会自动过滤当前站点的数据
     * 
     * @return 充值配置
     */
    @GetMapping("/config")
    public Result<RechargeConfigResponse> getConfig() {
        log.info("收到获取充值配置请求");
        try {
            RechargeConfigResponse config = rechargeConfigService.getActiveConfig();
            return Result.success("获取配置成功", config);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取充值配置失败", e);
            return Result.error("获取配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 创建充值订单
     * 
     * @param request 创建订单请求
     * @param token Token（从请求头获取）
     * @return 订单响应
     */
    @PostMapping("/create")
    public Result<RechargeOrderResponse> createOrder(
            @Valid @RequestBody RechargeOrderRequest request,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest httpServletRequest) {
        try {
            // 从Token中提取用户ID
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            
            String userAgent = httpServletRequest.getHeader("User-Agent");
            RechargeOrderResponse response = rechargeService.createOrder(userId, request, userAgent);
            return Result.success("创建订单成功", response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return Result.error("创建订单失败，请联系客服");
        }
    }
    
    /**
     * 微信支付回调
     * 微信支付回调使用JSON格式，需要从请求体中读取原始字符串
     * 
     * @param request HTTP请求对象
     * @return 处理结果（JSON）
     */
    @PostMapping("/callback/wechat")
    public String wechatCallback(HttpServletRequest request) {
        try {
            InputStream inputStream = request.getInputStream();
            String callbackBody = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);

            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames != null && headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }

            boolean success = rechargeService.handleWechatPaymentCallbackV3(callbackBody, headers);
            if (success) {
                return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
            } else {
                return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
            }
        } catch (Exception e) {
            log.error("处理微信支付回调失败", e);
            return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
        }
    }
    
    /**
     * 支付宝支付回调
     * 支付宝支付回调使用form-data格式（application/x-www-form-urlencoded），需要从请求参数中获取
     * 
     * @param request HTTP请求对象
     * @return 处理结果（success或fail）
     */
    @PostMapping("/callback/alipay")
    public String alipayCallback(HttpServletRequest request) {
        try {
            // 从请求参数中获取所有回调数据
            Map<String, String> callbackData = new HashMap<>();
            Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                if (values != null && values.length > 0) {
                    callbackData.put(key, values[0]);
                }
            }
            
            log.info("收到支付宝支付回调，参数：{}", callbackData);
            
            // 处理支付宝支付回调
            boolean success = rechargeService.handlePaymentCallback("alipay", callbackData);
            if (success) {
                return "success";
            } else {
                return "fail";
            }
        } catch (Exception e) {
            log.error("处理支付宝支付回调失败", e);
            return "fail";
        }
    }
    
    /**
     * 查询订单详情
     * 
     * @param orderNo 订单号
     * @param token Token（从请求头获取）
     * @return 订单详情
     */
    @GetMapping("/order/{orderNo}")
    public Result<OrderQueryResponse> queryOrder(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // 从Token中提取用户ID
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            
            OrderQueryResponse response = rechargeService.queryOrder(orderNo, userId);
            return Result.success("查询成功", response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询订单失败", e);
            return Result.error("查询订单失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取用户订单列表
     * 
     * @param page 页码（默认1）
     * @param size 每页大小（默认10）
     * @param token Token（从请求头获取）
     * @return 订单列表
     */
    @GetMapping("/orders")
    public Result<Page<OrderQueryResponse>> getOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // 从Token中提取用户ID
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            
            Page<OrderQueryResponse> response = rechargeService.getUserOrders(userId, page, size);
            return Result.success("查询成功", response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询订单列表失败", e);
            return Result.error("查询订单列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 取消订单
     * 
     * @param orderNo 订单号
     * @param token Token（从请求头获取）
     * @return 处理结果
     */
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancelOrder(
            @PathVariable String orderNo,
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            // 从Token中提取用户ID
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            
            rechargeService.cancelOrder(orderNo, userId);
            return Result.success("取消订单成功");
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("取消订单失败", e);
            return Result.error("取消订单失败：" + e.getMessage());
        }
    }
}

