package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.service.admin.InvitationCodeService;
import com.meitou.admin.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/app/invitations")
@RequiredArgsConstructor
public class AppInvitationController {

    private final InvitationCodeService invitationCodeService;

    @PostMapping("/redeem")
    public Result<String> redeem(@RequestBody Map<String, String> request,
                                 @RequestHeader(value = "Authorization", required = false) String token) {
        Long userId = TokenUtil.getUserIdFromToken(token);
        if (userId == null) {
            return Result.error("未登录或Token无效");
        }
        
        String code = request.get("code");
        if (code == null || code.trim().isEmpty()) {
            return Result.error("请输入兑换码");
        }
        
        try {
            String msg = invitationCodeService.redeemCode(userId, code);
            return Result.success("兑换成功", msg);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}