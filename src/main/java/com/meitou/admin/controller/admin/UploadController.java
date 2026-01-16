package com.meitou.admin.controller.admin;

import com.meitou.admin.common.Result;
import com.meitou.admin.service.common.AliyunOssService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端文件上传控制器
 * 注入 FileStorageService 接口，根据配置自动使用对应的实现类（腾讯云COS或阿里云OSS）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class UploadController {
    
    /**
     * 注入文件存储服务接口
     * Spring会根据 @ConditionalOnProperty 配置自动选择对应的实现类
     */
    private final FileStorageService fileStorageService;
    private final AliyunOssService aliyunOssService;
    
    /**
     * 上传文件
     * 
     * @param file 要上传的文件
     * @param folder 存储文件夹路径（可选，如 "images/", "videos/" 等）
     * @return 上传结果，包含文件的访问URL
     */
    @PostMapping
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder
    ) {
        try {
            // 验证文件
            if (file == null || file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }
            
            // 调用文件存储服务上传文件
            String fileUrl = fileStorageService.upload(file, folder);
            
            log.info("文件上传成功：{} -> {}", file.getOriginalFilename(), fileUrl);
            return Result.success("上传成功", fileUrl);
            
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return Result.error("文件上传失败：" + e.getMessage());
        }
    }
    
    /**
     * 上传图片（便捷接口）
     * 
     * @param file 要上传的图片文件
     * @return 上传结果，包含图片的访问URL
     */
    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return upload(file, "images/");
    }
    
    /**
     * 上传视频（便捷接口）
     * 
     * @param file 要上传的视频文件
     * @return 上传结果，包含视频的访问URL
     */
    @PostMapping("/video")
    public Result<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        return upload(file, "videos/");
    }

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

