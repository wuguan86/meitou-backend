package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.dto.app.PromptOptimizeRequest;
import com.meitou.admin.entity.AnalysisRecord;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.entity.PromptHelperConfig;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.admin.PromptHelperConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptOptimizeService {

    private final ApiPlatformService apiPlatformService;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final PointsLedgerService pointsLedgerService;
    private final PromptHelperConfigService promptHelperConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 提示词优化
     */
    public SseEmitter optimizePrompt(PromptOptimizeRequest request, Long userId) {
        // 1. 查找平台
        ApiPlatform platform = apiPlatformService.getPlatformByTypeAndModel("prompt_optimize", request.getModel(), null);

        // 如果未找到指定模型的平台，且请求的是默认模型(gpt-4o-mini)或空，则尝试使用第一个可用的提示词优化平台
        if (platform == null && (request.getModel() == null || request.getModel().isEmpty() || "gpt-4o-mini".equals(request.getModel()))) {
            List<ApiPlatform> platforms = apiPlatformService.getPlatformsByTypeWithDecryptedKey("prompt_optimize", null);
            if (!platforms.isEmpty()) {
                platform = platforms.get(0);
                // 更新请求中的模型名为平台支持的第一个模型
                String firstModel = getFirstSupportedModel(platform.getSupportedModels());
                if (firstModel != null) {
                    request.setModel(firstModel);
                }
            }
        }

        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(), "提示词优化平台未配置");
        }

        // 2. 查找接口
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platform.getId());
        ApiInterface apiInterface = interfaces.stream()
                .findFirst()
                .orElse(null);

        if (apiInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "提示词优化接口未配置");
        }

        // Save Analysis Record (Pending)
        AnalysisRecord analysisRecord = new AnalysisRecord();
        analysisRecord.setUserId(userId);
        analysisRecord.setType("prompt");
        analysisRecord.setContent(request.getPrompt());
        analysisRecord.setStatus(0); // Pending
        analysisRecord.setSiteId(SiteContext.getSiteId());
        
        // 如果有图片，记录图片URL到extra字段或者content中
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            try {
                Map<String, Object> extra = new HashMap<>();
                extra.put("images", request.getImages());
                analysisRecord.setExtra(objectMapper.writeValueAsString(extra));
            } catch (Exception e) {
                log.warn("序列化图片列表失败", e);
            }
        }
        
        analysisRecordMapper.insert(analysisRecord);

        // Deduct points
        PromptHelperConfig config = promptHelperConfigService.getConfig();
        int cost = (config != null && config.getComputeConsumption() != null) ? config.getComputeConsumption() : 20;
        final int finalCost = cost;
        
        if (cost > 0) {
            try {
                pointsLedgerService.deduct(userId, cost, "prompt_optimization", analysisRecord.getId(), "提示词优化消耗");
            } catch (Exception e) {
                // Mark record as failed if deduction fails
                analysisRecord.setStatus(2);
                analysisRecord.setErrorMsg("余额不足或扣费失败: " + e.getMessage());
                analysisRecordMapper.updateById(analysisRecord);
                throw e;
            }
        }

        // 3. 构建请求
        // Set timeout to 3 minutes (180000ms) to accommodate long generation times and avoid race conditions with OkHttp timeout (60s)
        SseEmitter emitter = new SseEmitter(180000L); 

        // Handle timeout and completion to avoid "ResponseBodyEmitter has already completed" errors
        emitter.onTimeout(emitter::complete);
        emitter.onError((e) -> emitter.complete());

        try {
            // 处理图片输入：如果有图片，需要重新构建messages
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                rebuildMessagesWithImages(request);
            }

            okhttp3.MediaType JSON = okhttp3.MediaType.get("application/json; charset=utf-8");
            String jsonBody = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(jsonBody, JSON);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiInterface.getUrl())
                    .post(body);

            // Add headers
            if (apiInterface.getHeaders() != null) {
                try {
                    JsonNode headersNode = objectMapper.readTree(apiInterface.getHeaders());
                    final ApiPlatform finalPlatform = platform;
                    headersNode.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue().asText();
                        if (value.contains("{apiKey}") && finalPlatform.getApiKey() != null) {
                            value = value.replace("{apiKey}", finalPlatform.getApiKey());
                        }
                        requestBuilder.addHeader(key, value);
                    });
                } catch (Exception e) {
                    log.warn("解析headers失败", e);
                }
            }

            // Ensure Authorization header if not present
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + platform.getApiKey());
            }

            Request okRequest = requestBuilder.build();

            // 4. Execute
            okHttpClient.newCall(okRequest).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Update Analysis Record (Failed)
                    analysisRecord.setStatus(2);
                    String errorMsg = "请求失败: " + e.getMessage();
                    analysisRecord.setErrorMsg(errorMsg);
                    analysisRecordMapper.updateById(analysisRecord);

                    // Refund
                    if (finalCost > 0) {
                        pointsLedgerService.refund(userId, "prompt_optimization", analysisRecord.getId(), "提示词优化失败退款");
                    }

                    try {
                        emitter.send(SseEmitter.event().name("error").data(errorMsg));
                        emitter.complete();
                    } catch (Exception ex) {
                        // Log but ignore if emitter is already completed
                        log.warn("Failed to send SSE error message (likely client disconnected or timed out): {}", ex.getMessage());
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    StringBuilder fullResponse = new StringBuilder();
                    
                    // State for thinking process filtering
                    // Using array to allow modification inside lambda/anonymous class
                    boolean[] isThinking = {false};
                    StringBuilder contentBuffer = new StringBuilder();

                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            // Update Analysis Record (Failed)
                            analysisRecord.setStatus(2);
                            String errorMsg = "系统繁忙，请稍后再试";
                            analysisRecord.setErrorMsg(errorMsg);
                            analysisRecordMapper.updateById(analysisRecord);

                            if (finalCost > 0) {
                                pointsLedgerService.refund(userId, "prompt_optimization", analysisRecord.getId(), "提示词优化失败退款");
                            }

                            try {
                                emitter.send(SseEmitter.event().name("error").data(errorMsg));
                                emitter.complete();
                            } catch (Exception ex) {
                                log.warn("Failed to send SSE error message: {}", ex.getMessage());
                            }
                            return;
                        }

                        if (responseBody == null) {
                            // Update Analysis Record (Failed)
                            analysisRecord.setStatus(2);
                            String errorMsg = "系统繁忙，请稍后再试";
                            analysisRecord.setErrorMsg(errorMsg);
                            analysisRecordMapper.updateById(analysisRecord);

                            if (finalCost > 0) {
                                pointsLedgerService.refund(userId, "prompt_optimization", analysisRecord.getId(), "提示词优化失败退款");
                            }

                            try {
                                emitter.send(SseEmitter.event().name("error").data(errorMsg));
                                emitter.complete();
                            } catch (Exception ex) {
                                log.warn("Failed to send SSE error message: {}", ex.getMessage());
                            }
                            return;
                        }

                        // Robust stream processing
                        okio.BufferedSource source = responseBody.source();
                        boolean isFinished = false;
                        StringBuilder lineBuffer = new StringBuilder();
                        byte[] buffer = new byte[8192];

                        while (!source.exhausted()) {
                            int read = source.read(buffer);
                            if (read == -1) break;

                            String chunk = new String(buffer, 0, read);
                            lineBuffer.append(chunk);

                            int newlineIndex;
                            while ((newlineIndex = lineBuffer.indexOf("\n")) != -1) {
                                String line = lineBuffer.substring(0, newlineIndex).trim();
                                lineBuffer.delete(0, newlineIndex + 1);

                                if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:")) {
                                    continue;
                                }

                                String payload = line.startsWith("data:") ? line.substring(5).trim() : line;
                                
                                if ("[DONE]".equals(payload)) {
                                    isFinished = true;
                                    break;
                                }

                                try {
                                    JsonNode node = objectMapper.readTree(payload);
                                    if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                                        JsonNode choice = node.get("choices").get(0);
                                        if (choice.has("delta") && choice.get("delta").has("content")) {
                                            String content = choice.get("delta").get("content").asText();
                                            processContent(content, contentBuffer, isThinking, emitter, fullResponse, objectMapper);
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore parsing errors for non-JSON lines
                                }
                            }
                            if (isFinished) break;
                        }
                        
                        // Process any remaining content in buffer (if not thinking)
                        if (!isThinking[0] && contentBuffer.length() > 0) {
                             sendToClient(contentBuffer.toString(), emitter, fullResponse, objectMapper);
                        }

                        // Update Analysis Record (Success)
                        analysisRecord.setStatus(1);
                        analysisRecord.setResult(fullResponse.toString());
                        analysisRecordMapper.updateById(analysisRecord);

                        emitter.complete();
                    } catch (Exception e) {
                        log.error("处理响应流失败", e);
                        // Update Analysis Record (Failed)
                        analysisRecord.setStatus(2);
                        analysisRecord.setErrorMsg("处理响应失败: " + e.getMessage());
                        analysisRecordMapper.updateById(analysisRecord);
                        
                        try {
                            emitter.send(SseEmitter.event().name("error").data("处理响应失败"));
                            emitter.complete();
                        } catch (Exception ex) {
                            // ignore
                        }
                    }
                }
            });

        } catch (Exception e) {
            log.error("构建请求失败", e);
            // Update Analysis Record (Failed)
            analysisRecord.setStatus(2);
            analysisRecord.setErrorMsg("构建请求失败: " + e.getMessage());
            analysisRecordMapper.updateById(analysisRecord);

            if (finalCost > 0) {
                pointsLedgerService.refund(userId, "prompt_optimization", analysisRecord.getId(), "提示词优化失败退款");
            }
            throw new BusinessException(ErrorCode.API_CALL_FAILED.getCode(), "构建请求失败");
        }

        return emitter;
    }

    /**
     * 重构消息列表以支持图片输入
     * 将文本内容转换为多模态格式 (GPT-4 Vision compatible)
     */
    private void rebuildMessagesWithImages(PromptOptimizeRequest request) {
        List<Map<String, Object>> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // 找到最后一条用户消息
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) {
                Object contentObj = msg.get("content");
                String textContent = contentObj != null ? contentObj.toString() : "";
                
                // 构建新的content列表
                List<Map<String, Object>> newContent = new ArrayList<>();
                
                // 添加文本
                Map<String, Object> textPart = new HashMap<>();
                textPart.put("type", "text");
                textPart.put("text", textContent);
                newContent.add(textPart);
                
                // 添加图片
                for (String imageUrl : request.getImages()) {
                    Map<String, Object> imagePart = new HashMap<>();
                    imagePart.put("type", "image_url");
                    Map<String, String> imageUrlMap = new HashMap<>();
                    imageUrlMap.put("url", imageUrl);
                    imagePart.put("image_url", imageUrlMap);
                    newContent.add(imagePart);
                }
                
                // 替换原来的content
                msg.put("content", newContent);
                break; // 只修改最后一条用户消息
            }
        }
    }

    private void sendToClient(String content, SseEmitter emitter, StringBuilder fullResponse, ObjectMapper objectMapper) throws IOException {
        fullResponse.append(content);
        
        // Wrap content in OpenAI-compatible JSON structure for frontend
        Map<String, Object> delta = new HashMap<>();
        delta.put("content", content);
        
        Map<String, Object> choiceMap = new HashMap<>();
        choiceMap.put("delta", delta);
        
        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(choiceMap);
        
        Map<String, Object> chunk = new HashMap<>();
        chunk.put("choices", choices);
        
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
    }

    private void processContent(String content, StringBuilder contentBuffer, boolean[] isThinking, SseEmitter emitter, StringBuilder fullResponse, ObjectMapper objectMapper) throws IOException {
        contentBuffer.append(content);

        // Loop to handle multiple tags in one chunk
        while (true) {
            if (isThinking[0]) {
                int endTagIndex = contentBuffer.indexOf("</think>");
                if (endTagIndex != -1) {
                    isThinking[0] = false;
                    contentBuffer.delete(0, endTagIndex + "</think>".length());
                    // Continue loop to process remaining buffer
                } else {
                    // Keep tail for potential split tag
                    if (contentBuffer.length() > 8) { // </think> is 8 chars
                        contentBuffer.delete(0, contentBuffer.length() - 8);
                    }
                    break;
                }
            } else {
                int startTagIndex = contentBuffer.indexOf("<think>");
                if (startTagIndex != -1) {
                    isThinking[0] = true;
                    String safeContent = contentBuffer.substring(0, startTagIndex);
                    if (!safeContent.isEmpty()) {
                        sendToClient(safeContent, emitter, fullResponse, objectMapper);
                    }
                    contentBuffer.delete(0, startTagIndex + "<think>".length());
                    // Continue loop to check for immediate closing tag
                } else {
                    // No tag found
                    if (contentBuffer.length() > 7) { // <think> is 7 chars
                        String safeContent = contentBuffer.substring(0, contentBuffer.length() - 7);
                        sendToClient(safeContent, emitter, fullResponse, objectMapper);
                        contentBuffer.delete(0, contentBuffer.length() - 7);
                    }
                    break;
                }
            }
        }
    }

    private String getFirstSupportedModel(String supportedModels) {
        if (supportedModels == null || supportedModels.trim().isEmpty()) {
            return null;
        }
        try {
            if (supportedModels.trim().startsWith("[")) {
                // JSON 格式
                JsonNode modelsNode = objectMapper.readTree(supportedModels);
                if (modelsNode.isArray() && modelsNode.size() > 0) {
                    JsonNode m = modelsNode.get(0);
                    if (m.isTextual()) {
                        return m.asText();
                    } else if (m.isObject()) {
                        if (m.has("name")) return m.get("name").asText();
                        if (m.has("id")) return m.get("id").asText();
                        if (m.has("value")) return m.get("value").asText();
                    }
                }
            } else {
                // 旧格式 # 分割
                String[] models = supportedModels.split("#");
                if (models.length > 0) {
                    return models[0].trim();
                }
            }
        } catch (Exception e) {
            log.warn("解析支持模型失败: {}", supportedModels, e);
            // 尝试简单分割
            String[] models = supportedModels.split("#");
            if (models.length > 0) {
                return models[0].trim();
            }
        }
        return null;
    }
}
