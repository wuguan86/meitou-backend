package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.SaveCharacterRequest;
import com.meitou.admin.entity.Character;
import com.meitou.admin.service.app.CharacterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/character")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    /**
     * 保存角色视频
     *
     * @param request 请求参数
     * @param userId  当前用户ID
     * @return 结果
     */
    @PostMapping("/save")
    public Result<Map<String, Object>> saveCharacterVideo(
            @RequestBody SaveCharacterRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]请求保存角色视频, pid: {}", userId, request.getPid());
        return characterService.saveCharacterVideo(request, userId);
    }

    /**
     * 获取用户角色列表
     *
     * @param userId 当前用户ID
     * @return 角色列表
     */
    @GetMapping("/list")
    public Result<List<Character>> getUserCharacters(@AuthenticationPrincipal Long userId) {
        return Result.success(characterService.getUserCharacters(userId));
    }

    /**
     * 删除角色
     *
     * @param id     角色ID
     * @param userId 当前用户ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCharacter(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        characterService.deleteCharacter(id, userId);
        return Result.success("删除成功");
    }
}
