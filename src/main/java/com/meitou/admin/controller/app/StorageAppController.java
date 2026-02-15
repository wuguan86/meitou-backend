package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.service.common.AliyunOssService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 用户端文件存储控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/storage")
@RequiredArgsConstructor
public class StorageAppController {
    
    private final FileStorageService fileStorageService;
    private final AliyunOssService aliyunOssService;
    
    /**
     * 获取文件访问URL
     * 
     * @param key 文件的Key或完整URL
     * @return 签名后的URL或原URL
     */
    @GetMapping("/url")
    public Result<String> getFileUrl(@RequestParam("key") String key) {
        try {
            String url = aliyunOssService.getSignedUrl(key);
            if (url == null || url.isEmpty() || url.equals(key)) {
                url = fileStorageService.getFileUrl(key);
            }
            return Result.success("获取成功", url);
        } catch (Exception e) {
            log.error("获取文件URL失败：{}", e.getMessage(), e);
            return Result.error("获取文件URL失败：" + e.getMessage());
        }
    }

    @GetMapping("/download")
    public Result<String> getDownloadUrl(@RequestParam("key") String key,
                                         @RequestParam(value = "filename", required = false) String filename) {
        try {
            String url = aliyunOssService.getSignedDownloadUrl(key, filename);
            if (url == null || url.isEmpty() || url.equals(key)) {
                url = fileStorageService.getFileUrl(key);
            }
            url = appendContentDisposition(url, filename);
            return Result.success("获取成功", url);
        } catch (Exception e) {
            log.error("获取下载URL失败：{}", e.getMessage(), e);
            return Result.error("获取下载URL失败：" + e.getMessage());
        }
    }

    private String appendContentDisposition(String url, String filename) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (filename == null || filename.trim().isEmpty()) {
            return url;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return url;
        }
        if (url.toLowerCase().contains("response-content-disposition=")) {
            return url;
        }
        String disposition = buildContentDisposition(filename);
        if (disposition == null || disposition.isEmpty()) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        String encoded = URLEncoder.encode(disposition, StandardCharsets.UTF_8).replace("+", "%20");
        return url + separator + "response-content-disposition=" + encoded;
    }

    private String buildContentDisposition(String filename) {
        if (filename == null) {
            return null;
        }
        String trimmed = filename.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String safeName = trimmed.replace("\\", "_").replace("/", "_").replace("\"", "_");
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encoded;
    }
}
