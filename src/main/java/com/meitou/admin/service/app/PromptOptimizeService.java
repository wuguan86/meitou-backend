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
        SseEmitter emitter = new SseEmitter(60000L); // 1 minute timeout

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
                    headersNode.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue().asText();
                        if (value.contains("{apiKey}") && platform.getApiKey() != null) {
                            value = value.replace("{apiKey}", platform.getApiKey());
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
                    } catch (IOException ex) {
                        log.error("发送SSE错误信息失败", ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    StringBuilder fullResponse = new StringBuilder();
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

                            emitter.send(SseEmitter.event().name("error").data(errorMsg));
                            emitter.complete();
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

                            emitter.send(SseEmitter.event().name("error").data(errorMsg));
                            emitter.complete();
                            return;
                        }

                        // Stream processing
                        okio.BufferedSource source = responseBody.source();
                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    try {
                                        JsonNode node = objectMapper.readTree(data);
                                        if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                                            JsonNode choice = node.get("choices").get(0);
                                            if (choice.has("delta") && choice.get("delta").has("content")) {
                                                String content = choice.get("delta").get("content").asText();
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
                                        }
                                    } catch (Exception e) {
                                        // Ignore parsing errors for non-JSON lines
                                    }
                                }
                            }
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
                        
                        // 注意：这里可能无法退款，因为已经开始处理响应了，但如果还没发送过任何数据，理论上可以退款
                        // 这里简化处理，不退款，因为可能已经部分成功
                        
                        try {
                            emitter.send(SseEmitter.event().name("error").data("处理响应失败"));
                            emitter.complete();
                        } catch (IOException ex) {
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
}
