package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.AssetFolder;
import com.meitou.admin.service.app.AssetFolderAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户端文件夹控制器
 * 处理文件夹的创建、查询、更新、删除等请求
 */
@Slf4j
@RestController
@RequestMapping("/api/app/folders")
@RequiredArgsConstructor
public class AssetFolderAppController {
    
    private final AssetFolderAppService folderService;
    
    /**
     * 创建文件夹
     * 
     * @param userId 用户ID（从请求头获取）
     * @param request 创建请求（包含name和parentPath）
     * @return 创建的文件夹
     */
    @PostMapping
    public Result<AssetFolder> createFolder(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, String> request
    ) {
        // 验证用户ID
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        
        String name = request.get("name");
        String parentPath = request.get("parentPath");
        
        if (name == null || name.trim().isEmpty()) {
            return Result.error(400, "文件夹名称不能为空");
        }
        
        AssetFolder folder = folderService.createFolder(userId, name.trim(), parentPath);
        log.info("用户 {} 创建文件夹成功：{} (路径：{})", userId, name, folder.getFolderPath());
        return Result.success("创建成功", folder);
    }
    
    /**
     * 获取用户的文件夹列表
     * 
     * @param userId 用户ID（从请求头获取）
     * @param parentPath 父文件夹路径（可选，如果为空则返回根目录下的文件夹）
     * @param all 是否获取所有文件夹（用于下拉选择，可选）
     * @return 文件夹列表
     */
    @GetMapping
    public Result<List<AssetFolder>> getFolders(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(value = "parentPath", required = false) String parentPath,
            @RequestParam(value = "all", required = false, defaultValue = "false") Boolean all
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            List<AssetFolder> folders;
            if (Boolean.TRUE.equals(all)) {
                // 获取所有文件夹（用于下拉选择）
                folders = folderService.getAllFolders(userId);
            } else {
                // 获取指定父目录下的文件夹
                folders = folderService.getFolders(userId, parentPath);
            }
            return Result.success(folders);
            
        } catch (Exception e) {
            log.error("获取文件夹列表失败：{}", e.getMessage(), e);
            return Result.error("获取文件夹列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新文件夹名称
     * 
     * @param userId 用户ID（从请求头获取）
     * @param folderId 文件夹ID
     * @param request 更新请求（包含name）
     * @return 更新后的文件夹
     */
    @PutMapping("/{folderId}")
    public Result<AssetFolder> updateFolder(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long folderId,
            @RequestBody Map<String, String> request
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            String name = request.get("name");
            if (name == null || name.trim().isEmpty()) {
                return Result.error(400, "文件夹名称不能为空");
            }
            
            AssetFolder folder = folderService.updateFolderName(userId, folderId, name.trim());
            return Result.success("更新成功", folder);
            
        } catch (Exception e) {
            log.error("更新文件夹失败：{}", e.getMessage(), e);
            return Result.error("更新文件夹失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除文件夹
     * 
     * @param userId 用户ID（从请求头获取）
     * @param folderId 文件夹ID
     * @return 删除结果
     */
    @DeleteMapping("/{folderId}")
    public Result<Void> deleteFolder(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long folderId
    ) {
        // 验证用户ID
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        
        folderService.deleteFolder(userId, folderId);
        return Result.success("删除成功", null);
    }
}
