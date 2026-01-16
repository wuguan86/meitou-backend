package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.UserAsset;
import com.meitou.admin.service.app.UserAssetAppService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 用户端资产控制器
 * 处理用户资产的上传、查询、删除等请求
 */
@Slf4j
@RestController
@RequestMapping("/api/app/assets")
@RequiredArgsConstructor
public class UserAssetAppController {
    
    private final UserAssetAppService assetService;
    private final FileStorageService fileStorageService;
    
    /**
     * 上传资产文件
     * 多租户插件会自动设置站点ID
     * 
     * @param userId 用户ID（从请求头或Token中获取，这里暂时通过参数传递）
     * @param file 上传的文件
     * @param title 标题（可选，默认为文件名）
     * @param type 类型：image-图片，video-视频，audio-音频（可选，根据文件类型自动判断）
     * @param folder 文件夹路径（可选）
     * @return 上传结果
     */
    @PostMapping("/upload")
    public Result<UserAsset> uploadAsset(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "folder", required = false) String folder
    ) {
        try {
            // 验证用户ID（实际项目中应从Token中解析）
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            // 验证文件
            if (file == null || file.isEmpty()) {
                return Result.error(400, "文件不能为空");
            }

            // 强制校验文件后缀（无论是否指定了type）
            String filename = file.getOriginalFilename();
            if (filename == null || filename.lastIndexOf(".") == -1) {
                return Result.error(400, "文件名无效");
            }
            String ext = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            
            boolean isValidExt = false;
            String detectedType = null;
            
            if (ext.matches("jpg|jpeg|png|gif|webp|bmp|svg|ico|tiff")) {
                isValidExt = true;
                detectedType = "image";
            } else if (ext.matches("mp4|avi|mov|wmv|flv|mkv|webm")) {
                isValidExt = true;
                detectedType = "video";
            } else if (ext.matches("mp3|wav|flac|aac|ogg|m4a")) {
                isValidExt = true;
                detectedType = "audio";
            }
            
            if (!isValidExt) {
                return Result.error(400, "不支持的文件类型: " + ext);
            }
            
            // 如果未指定类型，或者指定的类型与后缀不匹配（防止恶意绕过），则使用检测到的类型
            if (type == null || type.trim().isEmpty()) {
                type = detectedType;
            } else {
                // 如果用户指定了类型，但不匹配后缀检测到的类型，以后缀为准（或者报错）
                // 这里选择简单地以后缀检测为准，修正类型
                if (!type.equals(detectedType)) {
                    log.warn("用户上传文件类型 {} 与后缀检测类型 {} 不一致，修正为 {}", type, detectedType, detectedType);
                    type = detectedType;
                }
            }
            
            // 根据类型确定存储文件夹
            
            // 根据类型确定存储文件夹
            String storageFolder;
            switch (type) {
                case "image":
                    storageFolder = "images/";
                    break;
                case "video":
                    storageFolder = "videos/";
                    break;
                case "audio":
                    storageFolder = "audios/";
                    break;
                default:
                    storageFolder = "";
                    break;
            }
            
            // 如果有指定的文件夹，追加到存储路径
            if (folder != null && !folder.trim().isEmpty()) {
                storageFolder = storageFolder + folder.replace("\\", "/").trim();
                if (!storageFolder.endsWith("/")) {
                    storageFolder += "/";
                }
            }
            
            // 上传文件到存储服务
            String fileUrl = fileStorageService.upload(file, storageFolder);
            
            // 设置标题（如果未指定，使用文件名）
            if (title == null || title.trim().isEmpty()) {
                title = filename != null && filename.lastIndexOf(".") != -1 ? filename.substring(0, filename.lastIndexOf(".")) : "未命名文件";
            }
            
            // 创建资产记录
            UserAsset asset = assetService.uploadAsset(
                    userId,
                    title,
                    type,
                    fileUrl,
                    null, // 缩略图（图片类型可以后续生成，视频也可以提取第一帧）
                    folder // 存储用户指定的文件夹路径（用于前端展示和组织）
            );
            
            log.info("用户 {} 上传资产成功：{} (类型：{}, URL：{})", userId, title, type, fileUrl);
            return Result.success("上传成功", asset);
            
        } catch (Exception e) {
            log.error("上传资产失败：{}", e.getMessage(), e);
            return Result.error("上传失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取用户的资产列表
     * 
     * @param userId 用户ID（从请求头获取）
     * @param folder 文件夹路径（可选，如果为空则返回根目录的资产）
     * @param type 类型筛选（可选：image、video、audio、all）
     * @param ignoreFolder 是否忽略文件夹结构（可选，true表示返回所有层级的资产，默认false）
     * @return 资产列表
     */
    @GetMapping
    public Result<List<UserAsset>> getAssets(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "folder", required = false) String folder,
            @RequestParam(value = "type", required = false, defaultValue = "all") String type,
            @RequestParam(value = "ignoreFolder", required = false, defaultValue = "false") Boolean ignoreFolder
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            List<UserAsset> assets = assetService.getUserAssets(userId, folder, type, ignoreFolder);
            return Result.success(assets);
            
        } catch (Exception e) {
            log.error("获取资产列表失败：{}", e.getMessage(), e);
            return Result.error("获取资产列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取用户的文件夹列表
     * 
     * @param userId 用户ID（从请求头获取）
     * @return 文件夹路径列表
     */
    @GetMapping("/folders")
    public Result<List<String>> getFolders(
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            List<String> folders = assetService.getUserFolders(userId);
            return Result.success(folders);
            
        } catch (Exception e) {
            log.error("获取文件夹列表失败：{}", e.getMessage(), e);
            return Result.error("获取文件夹列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新资产信息（重命名、移动文件夹等）
     * 
     * @param userId 用户ID（从请求头获取）
     * @param assetId 资产ID
     * @param request 更新请求（包含title和folder）
     * @return 更新后的资产
     */
    @PutMapping("/{assetId}")
    public Result<UserAsset> updateAsset(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long assetId,
            @RequestBody Map<String, String> request
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            String title = request.get("title");
            String folder = request.get("folder");
            
            UserAsset asset = assetService.updateAsset(userId, assetId, title, folder);
            return Result.success("更新成功", asset);
            
        } catch (Exception e) {
            log.error("更新资产失败：{}", e.getMessage(), e);
            return Result.error("更新资产失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除资产
     * 
     * @param userId 用户ID（从请求头获取）
     * @param assetId 资产ID
     * @return 删除结果
     */
    @DeleteMapping("/{assetId}")
    public Result<Void> deleteAsset(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long assetId
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            assetService.deleteAsset(userId, assetId);
            return Result.success("删除成功", null);
            
        } catch (Exception e) {
            log.error("删除资产失败：{}", e.getMessage(), e);
            return Result.error("删除资产失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量删除资产
     * 
     * @param userId 用户ID（从请求头获取）
     * @param request 包含assetIds列表的请求
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<Void> deleteAssets(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, List<Long>> request
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            List<Long> assetIds = request.get("assetIds");
            if (assetIds == null || assetIds.isEmpty()) {
                return Result.error(400, "请选择要删除的资产");
            }
            
            assetService.deleteAssets(userId, assetIds);
            return Result.success("批量删除成功", null);
            
        } catch (Exception e) {
            log.error("批量删除资产失败：{}", e.getMessage(), e);
            return Result.error("批量删除资产失败：" + e.getMessage());
        }
    }
}
