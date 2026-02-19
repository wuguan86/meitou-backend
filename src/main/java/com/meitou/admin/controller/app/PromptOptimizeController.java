package com.meitou.admin.controller.app;

import com.meitou.admin.dto.app.PromptOptimizeRequest;
import com.meitou.admin.service.app.PromptOptimizeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/app/prompt-optimize")
@RequiredArgsConstructor
public class PromptOptimizeController {

    private final PromptOptimizeService promptOptimizeService;

    /**
     * 提示词优化
     */
    @PostMapping("/optimize")
    public SseEmitter optimizePrompt(
            @RequestBody PromptOptimizeRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]发起提示词优化请求", userId);
        return promptOptimizeService.optimizePrompt(request, userId);
    }
}
