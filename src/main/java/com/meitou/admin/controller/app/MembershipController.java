package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.MembershipOrderCreateRequest;
import com.meitou.admin.dto.app.MembershipStatusResponse;
import com.meitou.admin.dto.app.RechargeOrderResponse;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.service.app.MembershipOrderService;
import com.meitou.admin.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/app/membership")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipOrderService membershipOrderService;

    @PostMapping("/order/create")
    public Result<RechargeOrderResponse> createOrder(@Valid @RequestBody MembershipOrderCreateRequest request,
                                                     @RequestHeader(value = "Authorization", required = false) String token,
                                                     HttpServletRequest httpServletRequest) {
        try {
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            String userAgent = httpServletRequest.getHeader("User-Agent");
            RechargeOrderResponse response = membershipOrderService.createOrder(userId, request, userAgent);
            return Result.success("创建订单成功", response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("创建会员订单失败", e);
            return Result.error("创建订单失败，请联系客服");
        }
    }

    @GetMapping("/status")
    public Result<MembershipStatusResponse> status(@RequestHeader(value = "Authorization", required = false) String token) {
        try {
            Long userId = TokenUtil.getUserIdFromToken(token);
            if (userId == null) {
                return Result.error("未登录或Token无效");
            }
            MembershipStatusResponse response = membershipOrderService.getStatus(userId);
            return Result.success("查询成功", response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询会员状态失败", e);
            return Result.error("查询失败，请稍后再试");
        }
    }
}

