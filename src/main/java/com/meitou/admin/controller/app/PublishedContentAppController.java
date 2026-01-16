package com.meitou.admin.controller.app;

import com.meitou.admin.common.Result;
import com.meitou.admin.entity.PublishedContent;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.service.app.LikeService;
import com.meitou.admin.service.app.PublishedContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户端发布内容控制器
 * 处理发布内容的发布、查询、点赞等请求
 */
@Slf4j
@RestController
@RequestMapping("/api/app/published-contents")
@RequiredArgsConstructor
public class PublishedContentAppController {
    
    private final PublishedContentService contentService;
    private final LikeService likeService;
    
    /**
     * 发布内容
     * 
     * @param userId 用户ID（从请求头获取）
     * @param request 发布请求
     * @return 发布结果
     */
    @PostMapping
    public Result<PublishedContent> publishContent(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody Map<String, Object> request
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            // 获取请求参数
            String title = (String) request.get("title");
            String description = (String) request.get("description");
            String contentUrl = (String) request.get("contentUrl");
            String thumbnail = (String) request.get("thumbnail");
            String type = (String) request.get("type"); // image 或 video
            String generationType = (String) request.get("generationType"); // txt2img/img2img/txt2video/img2video
            String generationConfig = (String) request.get("generationConfig"); // JSON字符串
            Long generationRecordId = null;
            Object generationRecordIdValue = request.get("generationRecordId");
            if (generationRecordIdValue instanceof Number) {
                generationRecordId = ((Number) generationRecordIdValue).longValue();
            } else if (generationRecordIdValue instanceof String && !((String) generationRecordIdValue).trim().isEmpty()) {
                generationRecordId = Long.parseLong(((String) generationRecordIdValue).trim());
            }
            
            // 验证必填字段
            if (title == null || title.trim().isEmpty()) {
                return Result.error(400, "标题不能为空");
            }
            if (contentUrl == null || contentUrl.trim().isEmpty()) {
                return Result.error(400, "内容URL不能为空");
            }
            if (type == null || (!type.equals("image") && !type.equals("video"))) {
                return Result.error(400, "类型必须是image或video");
            }
            if (generationType == null || generationType.trim().isEmpty()) {
                return Result.error(400, "生成类型不能为空");
            }
            if (generationRecordId == null) {
                return Result.error(400, "生成记录ID不能为空");
            }
            
            // 发布内容
            PublishedContent content = contentService.publishContent(
                    userId, title, description, contentUrl, thumbnail,
                    type, generationType, generationConfig, generationRecordId
            );
            
            log.info("用户 {} 发布内容成功：{} (类型：{}, URL：{})", userId, title, type, contentUrl);
            return Result.success("发布成功", content);
            
        } catch (BusinessException e) {
            log.warn("发布内容业务失败：code={}, message={}", e.getCode(), e.getMessage());
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("发布内容失败：{}", e.getMessage(), e);
            return Result.error("发布失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取发布内容列表（分页）
     * 
     * @param type 类型筛选（可选：all/image/video）
     * @param page 页码，默认1
     * @param pageSize 每页数量，默认10
     * @param userId 当前用户ID（可选，用于获取点赞状态）
     * @return 分页发布内容列表
     */
    @GetMapping
    public Result<IPage<PublishedContent>> getPublishedContents(
            @RequestParam(value = "type", required = false, defaultValue = "all") String type,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        try {
            Page<PublishedContent> pageParam = new Page<>(page, pageSize);
            IPage<PublishedContent> result = contentService.getPublishedContents(pageParam, type, userId);
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("获取发布内容列表失败：{}", e.getMessage(), e);
            return Result.error("获取发布内容列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取发布内容详情
     * 
     * @param id 发布内容ID
     * @return 发布内容详情
     */
    @GetMapping("/{id}")
    public Result<PublishedContent> getPublishedContentDetail(@PathVariable Long id) {
        try {
            PublishedContent content = contentService.getPublishedContentById(id);
            return Result.success(content);
            
        } catch (Exception e) {
            log.error("获取发布内容详情失败：{}", e.getMessage(), e);
            return Result.error("获取发布内容详情失败：" + e.getMessage());
        }
    }
    
    /**
     * 点赞/取消点赞
     * 
     * @param userId 用户ID（从请求头获取）
     * @param id 发布内容ID
     * @return 点赞结果（包含是否已点赞和当前点赞数）
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> toggleLike(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            // 切换点赞状态
            boolean isLiked = likeService.toggleLike(userId, id);
            
            // 获取更新后的发布内容（包含最新的点赞数）
            PublishedContent content = contentService.getPublishedContentById(id);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("isLiked", isLiked);
            response.put("likeCount", content.getLikeCount());
            
            return Result.success(isLiked ? "点赞成功" : "取消点赞成功", response);
            
        } catch (Exception e) {
            log.error("点赞操作失败：{}", e.getMessage(), e);
            return Result.error("点赞操作失败：" + e.getMessage());
        }
    }
    
    /**
     * 检查用户是否已点赞
     * 
     * @param userId 用户ID（从请求头获取）
     * @param id 发布内容ID
     * @return 是否已点赞
     */
    @GetMapping("/{id}/like-status")
    public Result<Map<String, Object>> getLikeStatus(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id
    ) {
        try {
            // 如果用户未登录，返回未点赞
            boolean isLiked = false;
            if (userId != null) {
                isLiked = likeService.isLiked(userId, id);
            }
            
            // 获取发布内容（包含点赞数）
            PublishedContent content = contentService.getPublishedContentById(id);
            
            // 构建响应
            Map<String, Object> response = new HashMap<>();
            response.put("isLiked", isLiked);
            response.put("likeCount", content.getLikeCount());
            
            return Result.success(response);
            
        } catch (Exception e) {
            log.error("获取点赞状态失败：{}", e.getMessage(), e);
            return Result.error("获取点赞状态失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除发布内容（仅限发布者）
     * 
     * @param userId 用户ID（从请求头获取）
     * @param id 发布内容ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deletePublishedContent(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long id
    ) {
        try {
            // 验证用户ID
            if (userId == null) {
                return Result.error(401, "用户未登录");
            }
            
            // 删除发布内容
            contentService.deleteContent(userId, id);
            
            log.info("用户 {} 删除发布内容成功：{}", userId, id);
            return Result.success("删除成功", null);
            
        } catch (Exception e) {
            log.error("删除发布内容失败：{}", e.getMessage(), e);
            return Result.error("删除发布内容失败：" + e.getMessage());
        }
    }
}
