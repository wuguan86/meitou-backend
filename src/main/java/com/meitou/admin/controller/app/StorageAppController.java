package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.service.common.AliyunOssService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
}
