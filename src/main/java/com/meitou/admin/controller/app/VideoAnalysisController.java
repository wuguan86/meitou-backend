package com.meitou.admin.controller.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.PlatformModelResponse;
import com.meitou.admin.dto.app.VideoAnalysisRequest;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.app.VideoAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;


import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 视频分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/app/video-analysis")
@RequiredArgsConstructor
public class VideoAnalysisController {

    private final VideoAnalysisService videoAnalysisService;
    private final ApiPlatformService apiPlatformService;

    /**
     * 获取视频分析模型列表
     *
     * @return 平台模型列表
     */
    @GetMapping("/models")
    public Result<List<PlatformModelResponse>> getModels() {
        return Result.success(getModelsByType("video_analysis"));
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
     * 视频分析（流式响应）
     * 
     * @param request 请求参数
     * @param userId 当前用户ID
     * @return SSE流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> analyzeVideo(
            @RequestBody VideoAnalysisRequest request,
            @AuthenticationPrincipal Long userId) {
        SseEmitter emitter = videoAnalysisService.analyzeVideo(request, userId);
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);
    }
}
