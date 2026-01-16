package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.UpdateProfileRequest;
import com.meitou.admin.dto.app.UserLoginResponse;
import com.meitou.admin.service.app.AuthAppService;
import com.meitou.admin.storage.FileStorageService;
import com.meitou.admin.util.TokenUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/app/user")
@RequiredArgsConstructor
public class AppUserController {

    private final AuthAppService authAppService;
    private final FileStorageService fileStorageService;

    @PutMapping("/profile")
    public Result<UserLoginResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        Long userId = getCurrentUserId(token);
        if (userId == null) {
            return Result.error("未登录或Token无效");
        }
        UserLoginResponse response = authAppService.updateProfile(userId, request.getEmail(), request.getUsername(), request.getCompany());
        return Result.success("更新成功", response);
    }

    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String token
    ) {
        Long userId = getCurrentUserId(token);
        if (userId == null) {
            return Result.error("未登录或Token无效");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择头像文件");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return Result.error("仅支持图片格式");
        }

        try {
            // 使用云存储服务上传头像
            String avatarUrl = fileStorageService.upload(file, "avatars/");
            
            // 更新用户头像 URL
            authAppService.updateAvatarUrl(userId, avatarUrl);

            // 返回签名的URL供前端显示
            String signedUrl = fileStorageService.getFileUrl(avatarUrl);
            return Result.success("上传成功", signedUrl);
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    @GetMapping("/avatar/{filename}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) throws MalformedURLException {
        Path file = Paths.get(System.getProperty("user.dir"), "uploads", "avatars").resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new UrlResource(file.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "application/octet-stream";
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                contentType = probed;
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private Long getCurrentUserId(String token) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long) {
                return (Long) principal;
            }
            if (principal instanceof Number) {
                return ((Number) principal).longValue();
            }
            if (principal instanceof String) {
                try {
                    return Long.parseLong((String) principal);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return TokenUtil.getUserIdFromToken(token);
    }
}
