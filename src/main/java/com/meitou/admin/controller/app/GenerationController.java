package com.meitou.admin.controller.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.app.GenerationService;
import com.meitou.admin.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户端生成控制器
 * 处理文生图、图生图等请求
 */
@Slf4j
@RestController
@RequestMapping("/api/app/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;
    private final ApiPlatformService apiPlatformService;

    /**
     * 获取用户生成记录
     *
     * @param page 页码
     * @param size 每页数量
     * @param type 类型筛选 (可选)
     * @param userId 当前用户ID
     * @return 记录分页
     */
    @GetMapping("/records")
    public Result<Page<GenerationRecord>> getRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal Long userId) {
        return Result.success(generationService.getUserGenerationRecords(userId, page, size, type));
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/task/{recordId}")
    public Result<ImageGenerationResponse> getTaskStatus(@PathVariable Long recordId) {
        return Result.success(generationService.getTaskStatus(recordId));
    }

    /**
     * 文生图
     *
     * @param request 文生图请求
     * @param userId  当前用户ID
     * @return 生成结果
     */
    @PostMapping("/text-to-image")
    public Result<ImageGenerationResponse> textToImage(
            @RequestBody TextToImageRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]发起文生图请求: {}", userId, request.getPrompt());
        ImageGenerationResponse response = generationService.generateTextToImage(request, userId);
        return Result.success(response);
    }

    /**
     * 图生图
     *
     * @param request 图生图请求
     * @param userId  当前用户ID
     * @return 生成结果
     */
    @PostMapping("/image-to-image")
    public Result<ImageGenerationResponse> imageToImage(
            @RequestBody ImageToImageRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]发起图生图请求: {}", userId, request.getPrompt());
        ImageGenerationResponse response = generationService.generateImageToImage(request, userId);
        return Result.success(response);
    }

    /**
     * 文生视频
     *
     * @param request 文生视频请求
     * @param userId  当前用户ID
     * @return 生成结果
     */
    @PostMapping("/text-to-video")
    public Result<VideoGenerationResponse> textToVideo(
            @RequestBody TextToVideoRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]发起文生视频请求: {}", userId, request.getPrompt());
        VideoGenerationResponse response = generationService.generateTextToVideo(request, userId);
        return Result.success(response);
    }

    /**
     * 图生视频
     *
     * @param request 图生视频请求
     * @param userId  当前用户ID
     * @return 生成结果
     */
    @PostMapping("/image-to-video")
    public Result<VideoGenerationResponse> imageToVideo(
            @RequestBody ImageToVideoRequest request,
            @AuthenticationPrincipal Long userId) {
        // 注意：这里复用了TextToVideoRequest，实际可能需要ImageToVideoRequest，
        // 但根据GenerationService实现，image-to-video也是用TextToVideoRequest (或类似结构)
        // 检查GenerationService.generateImageToVideo的签名
        // 发现GenerationService.generateImageToVideo(TextToVideoRequest request, Long userId)
        // 确实是用TextToVideoRequest，其中包含imageUrl字段
        log.info("用户[{}]发起图生视频请求: {}", userId, request.getPrompt());
        VideoGenerationResponse response = generationService.generateImageToVideo(request, userId);
        return Result.success(response);
    }

    /**
     * 提示词优化
     */
    @PostMapping("/prompt-optimize")
    public SseEmitter optimizePrompt(
            @RequestBody PromptOptimizeRequest request,
            @AuthenticationPrincipal Long userId) {
        log.info("用户[{}]发起提示词优化请求", userId);
        return generationService.optimizePrompt(request, userId);
    }

    /**
     * 获取文生图模型列表
     *
     * @return 平台模型列表
     */
    @GetMapping("/text-to-image/models")
    public Result<List<PlatformModelResponse>> getTextToImageModels() {
        return Result.success(getModelsByType("txt2img"));
    }

    /**
     * 获取图生图模型列表
     *
     * @return 平台模型列表
     */
    @GetMapping("/image-to-image/models")
    public Result<List<PlatformModelResponse>> getImageToImageModels() {
        return Result.success(getModelsByType("img2img"));
    }

    /**
     * 获取文生视频模型列表
     *
     * @return 平台模型列表
     */
    @GetMapping("/text-to-video/models")
    public Result<List<PlatformModelResponse>> getTextToVideoModels() {
        return Result.success(getModelsByType("txt2video"));
    }

    /**
     * 获取图生视频模型列表
     *
     * @return 平台模型列表
     */
    @GetMapping("/image-to-video/models")
    public Result<List<PlatformModelResponse>> getImageToVideoModels() {
        return Result.success(getModelsByType("img2video"));
    }

    /**
     * 根据类型获取模型列表（通用方法）
     */
    private List<PlatformModelResponse> getModelsByType(String type) {
        // 获取对应类型的平台列表（这里siteId传null，表示获取所有或全局配置）
        List<ApiPlatform> platforms = apiPlatformService.getPlatformsByTypeWithDecryptedKey(type, null);
        ObjectMapper objectMapper = new ObjectMapper();
        
        return platforms.stream().map(platform -> {
            PlatformModelResponse response = new PlatformModelResponse();
            response.setPlatformId(platform.getId());
            response.setPlatformName(platform.getName());
            
            List<PlatformModelResponse.ModelInfo> modelInfos = new ArrayList<>();
            if (platform.getSupportedModels() != null && !platform.getSupportedModels().isEmpty()) {
                // 尝试解析为JSON (新格式)
                if (platform.getSupportedModels().trim().startsWith("[")) {
                    try {
                        JsonNode modelsNode = objectMapper.readTree(platform.getSupportedModels());
                        for (JsonNode m : modelsNode) {
                            PlatformModelResponse.ModelInfo info = new PlatformModelResponse.ModelInfo();
                            String name = m.has("name") ? m.get("name").asText() : "";
                            if (!name.isEmpty()) {
                                info.setId(name);
                                info.setName(m.has("label") && !m.get("label").asText().isEmpty() ? m.get("label").asText() : name);
                                
                                // 解析分辨率
                                if (m.has("resolutions") && m.get("resolutions").isArray()) {
                                    List<String> resolutions = new ArrayList<>();
                                    m.get("resolutions").forEach(r -> resolutions.add(r.asText()));
                                    info.setResolutions(resolutions);
                                }
                                
                                // 解析比例
                                if (m.has("ratios") && m.get("ratios").isArray()) {
                                    List<String> ratios = new ArrayList<>();
                                    m.get("ratios").forEach(r -> ratios.add(r.asText()));
                                    info.setRatios(ratios);
                                }
                                
                                // 解析时长
                                if (m.has("durations") && m.get("durations").isArray()) {
                                    List<Integer> durations = new ArrayList<>();
                                    m.get("durations").forEach(d -> durations.add(d.asInt()));
                                    info.setDurations(durations);
                                }
                                
                                // 解析数量
                                if (m.has("quantities") && m.get("quantities").isArray()) {
                                    List<Integer> quantities = new ArrayList<>();
                                    m.get("quantities").forEach(q -> quantities.add(q.asInt()));
                                    info.setQuantities(quantities);
                                }

                                // 解析消耗
                                if (m.has("defaultCost")) {
                                    info.setDefaultCost(m.get("defaultCost").asInt());
                                } else {
                                    info.setDefaultCost(10); // 默认消耗
                                }
                                
                                modelInfos.add(info);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析平台[{}]的模型配置JSON失败: {}", platform.getName(), e.getMessage());
                    }
                } else {
                    // 模型列表以#号分割 (旧格式兼容)
                    String[] models = platform.getSupportedModels().split("#");
                    for (String model : models) {
                        if (!model.trim().isEmpty()) {
                            PlatformModelResponse.ModelInfo info = new PlatformModelResponse.ModelInfo();
                            info.setId(model.trim());
                            info.setName(model.trim()); // 暂时使用ID作为名称
                            modelInfos.add(info);
                        }
                    }
                }
            }
            response.setModels(modelInfos);
            return response;
        }).collect(Collectors.toList());
    }

    /**
     * 获取单个生成记录详情
     *
     * @param id 记录ID
     * @param userId 当前用户ID
     * @return 记录详情
     */
    @GetMapping("/{id}")
    public Result<GenerationRecord> getRecord(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return Result.success(generationService.getGenerationRecord(id, userId));
    }

    /**
     * 删除生成记录
     *
     * @param id 记录ID
     * @param userId 当前用户ID
     * @return 结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRecord(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        generationService.deleteGenerationRecord(id, userId);
        return Result.success();
    }

    /**
     * 发布生成记录
     *
     * @param id 记录ID
     * @param userId 当前用户ID
     * @return 结果
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publishRecord(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @AuthenticationPrincipal Long principalUserId) {
        Long userId = principalUserId != null ? principalUserId : headerUserId;
        if (userId == null) {
            userId = TokenUtil.getUserIdFromToken(token);
        }
        if (userId == null) {
            return Result.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        generationService.publishGenerationRecord(id, userId);
        return Result.success();
    }

//    /**
//     * 提示词优化
//     */
//    @PostMapping("/prompt-optimize")
//    public SseEmitter optimizePrompt(
//            @RequestBody PromptOptimizeRequest request,
//            @AuthenticationPrincipal Long userId) {
//        log.info("用户[{}]发起提示词优化请求", userId);
//        return generationService.optimizePrompt(request, userId);
//    }
}
