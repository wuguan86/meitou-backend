package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meitou.admin.common.Result;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.dto.app.ImageAnalysisRequest;
import com.meitou.admin.entity.*;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.common.AliyunOssService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ImageAnalysisService {

    private final ApiPlatformService apiPlatformService;
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final TransactionTemplate transactionTemplate;
    private final AliyunOssService aliyunOssService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for analysis
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void sendBusinessErrorEvent(SseEmitter emitter, Integer code, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(Result.error(code, message))));
        } catch (Exception ignored) {
        }
    }

    private String resolveUnknownAnalysisError(Exception e) {
        String message = e != null ? e.getMessage() : null;
        if (message != null && message.contains("timed out")) {
            return "生成请求超时，请稍后重试";
        }
        return "系统繁忙，请稍后再试";
    }

    private String extractUpstreamErrorMessage(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.has("error")) {
            JsonNode err = node.get("error");
            if (err.isTextual()) {
                String msg = err.asText();
                return msg == null || msg.isBlank() ? null : msg;
            }
            if (err.isObject()) {
                if (err.has("message") && err.get("message").isTextual()) {
                    String msg = err.get("message").asText();
                    return msg == null || msg.isBlank() ? null : msg;
                }
                if (err.has("msg") && err.get("msg").isTextual()) {
                    String msg = err.get("msg").asText();
                    return msg == null || msg.isBlank() ? null : msg;
                }
            }
        }
        if (node.has("message") && node.get("message").isTextual()) {
            String msg = node.get("message").asText();
            return msg == null || msg.isBlank() ? null : msg;
        }
        if (node.has("msg") && node.get("msg").isTextual()) {
            String msg = node.get("msg").asText();
            return msg == null || msg.isBlank() ? null : msg;
        }
        return null;
    }

    private boolean handleStreamPayload(
            String payload,
            StringBuilder fullResponse,
            Long recordId,
            Long userId,
            Long recordSiteId,
            int finalCost,
            String finalModel,
            SseEmitter emitter
    ) {
        if (payload == null || payload.isBlank()) {
            return false;
        }

        if ("[DONE]".equals(payload)) {
            markSuccessIfPending(recordId, recordSiteId, fullResponse.toString());
            emitter.complete();
            return true;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            String upstreamError = extractUpstreamErrorMessage(node);
            if (upstreamError != null) {
                failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, upstreamError, "图片分析失败退款-" + finalModel);
                sendBusinessErrorEvent(emitter, ErrorCode.API_RESPONSE_ERROR.getCode(), upstreamError);
                emitter.complete();
                return true;
            }

            if (node.has("code") && node.get("code").canConvertToInt()) {
                int code = node.get("code").asInt();
                if (code != 0 && code != 200) {
                    String msg = extractUpstreamErrorMessage(node);
                    if (msg == null || msg.isBlank()) {
                        msg = "图片分析失败";
                    }
                    failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, msg, "图片分析失败退款-" + finalModel);
                    sendBusinessErrorEvent(emitter, code, msg);
                    emitter.complete();
                    return true;
                }
            }

            if (node.has("choices") && node.get("choices").isArray() && node.get("choices").size() > 0) {
                JsonNode choice = node.get("choices").get(0);
                if (choice.has("delta") && choice.get("delta").has("content")) {
                    String content = choice.get("delta").get("content").asText();
                    fullResponse.append(content);
                }
            }
        } catch (Exception ignored) {
        }

        try {
            emitter.send(SseEmitter.event().data(payload));
        } catch (IOException ignored) {
        }
        return false;
    }

    private void runWithSiteContext(Long siteId, Runnable runnable) {
        Long originalSiteId = SiteContext.getSiteId();
        try {
            SiteContext.setSiteId(siteId);
            runnable.run();
        } finally {
            if (originalSiteId == null) {
                SiteContext.clear();
            } else {
                SiteContext.setSiteId(originalSiteId);
            }
        }
    }

    public ImageAnalysisService(ApiPlatformService apiPlatformService,
                                UserMapper userMapper,
                                UserTransactionMapper userTransactionMapper,
                                AnalysisRecordMapper analysisRecordMapper,
                                AliyunOssService aliyunOssService,
                                TransactionTemplate transactionTemplate) {
        this.apiPlatformService = apiPlatformService;
        this.userMapper = userMapper;
        this.userTransactionMapper = userTransactionMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.aliyunOssService = aliyunOssService;
        this.transactionTemplate = transactionTemplate;
    }

    private void markSuccessIfPending(Long recordId, Long siteId, String result) {
        if (recordId == null || siteId == null) {
            return;
        }
        runWithSiteContext(siteId, () -> transactionTemplate.execute(status -> {
            UpdateWrapper<AnalysisRecord> update = new UpdateWrapper<>();
            update.eq("id", recordId);
            update.eq("status", 0);
            update.set("status", 1);
            update.set("result", truncate(result, 20000));
            update.set("updated_at", LocalDateTime.now());
            analysisRecordMapper.update(null, update);
            return null;
        }));
    }

    private void failAndRefundIfPending(Long recordId, Long userId, Long siteId, int cost, String errorMsg, String description) {
        if (recordId == null || siteId == null) {
            return;
        }
        if (cost < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣费配置异常");
        }
        String finalSafeErrorMsg = truncate(errorMsg, 500);

        runWithSiteContext(siteId, () -> transactionTemplate.execute(status -> {
            UpdateWrapper<AnalysisRecord> update = new UpdateWrapper<>();
            update.eq("id", recordId);
            update.eq("status", 0);
            update.set("status", 2);
            update.set("error_msg", finalSafeErrorMsg);
            update.set("updated_at", LocalDateTime.now());
            int updatedRows = analysisRecordMapper.update(null, update);
            if (updatedRows == 0) {
                return null;
            }

            if (cost > 0) {
                if (userId == null) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "退款用户缺失");
                }
                int balanceUpdated = userMapper.incrementBalance(userId, cost, LocalDateTime.now());
                if (balanceUpdated == 0) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR);
                }

                User userAfter = userMapper.selectById(userId);
                if (userAfter == null) {
                    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;

                UserTransaction transaction = new UserTransaction();
                transaction.setUserId(userId);
                transaction.setType("REFUND");
                transaction.setAmount(cost);
                transaction.setBalanceAfter(balanceAfter);
                transaction.setDescription(description);
                transaction.setReferenceId(recordId);
                transaction.setSiteId(siteId);
                int inserted = userTransactionMapper.insert(transaction);
                if (inserted <= 0) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "退款流水写入失败");
                }
            }

            return null;
        }));
    }

    public SseEmitter analyzeImage(ImageAnalysisRequest request, Long userId) {
        Long siteId = SiteContext.getSiteId();
        if (siteId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
        }
        if (request == null || request.getImage() == null || request.getImage().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "图片不能为空");
        }
        if (request.getImage().startsWith("blob:")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "图片上传失败，请重新上传");
        }
        if (request.getImage().startsWith("data:")) {
            try {
                request.setImage(aliyunOssService.uploadBase64(request.getImage(), "app/images/"));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "图片上传失败，请重新上传");
            }
        }

        // 1. Get User
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. Find Platform
        ApiPlatform platform = apiPlatformService.getPlatformByTypeAndModel("image_analysis", request.getModel(), SiteContext.getSiteId());
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(), "图片分析平台未配置");
        }

        // 3. Find Interface
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platform.getId());
        ApiInterface apiInterface = interfaces.stream()
                .filter(i -> i.getResponseMode() != null && !"Result".equals(i.getResponseMode()))
                .findFirst()
                .orElse(null);
        if (apiInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "图片分析接口未配置");
        }

        // 4. Get Config (Chart Profile & Cost)
        String chartProfile = "";
        int cost = 100; // Default
        String actualModel = request.getModel();

        if (platform.getSupportedModels() != null) {
            try {
                // Check if supportedModels is JSON array
                if (platform.getSupportedModels().trim().startsWith("[")) {
                    JsonNode models = objectMapper.readTree(platform.getSupportedModels());
                    for (JsonNode m : models) {
                        boolean isMatch = false;
                        if (m.has("name") && m.get("name").asText().equals(request.getModel())) {
                            isMatch = true;
                        } else if (m.has("id") && m.get("id").asText().equals(request.getModel())) {
                            isMatch = true;
                        } else if (m.has("value") && m.get("value").asText().equals(request.getModel())) {
                            isMatch = true;
                        }

                        if (isMatch) {
                            if (m.has("name")) {
                                actualModel = m.get("name").asText();
                            }
                            if (m.has("chartProfile")) {
                                chartProfile = m.get("chartProfile").asText();
                            }
                            if (m.has("defaultCost")) {
                                cost = m.get("defaultCost").asInt();
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse supported models", e);
            }
        }

        // 5. Deduct Credits
        int finalCost = cost;
        String finalModel = actualModel;
        if (finalCost < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣费配置异常");
        }

        AnalysisRecord analysisRecord = transactionTemplate.execute(status -> {
            if (finalCost > 0) {
                int updatedRows = userMapper.deductBalance(userId, finalCost, LocalDateTime.now());
                if (updatedRows == 0) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
                }
            }

            User userAfter = userMapper.selectById(userId);
            if (userAfter == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            int balanceAfter = userAfter.getBalance() != null ? userAfter.getBalance() : 0;
            if (balanceAfter < 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "算力余额异常");
            }

            // Save Analysis Record (Pending)
            AnalysisRecord record = new AnalysisRecord();
            record.setUserId(userId);
            record.setType("image");
            record.setContent(request.getImage());
            record.setStatus(0); // Pending
            record.setSiteId(siteId);
            int recordInserted = analysisRecordMapper.insert(record);
            if (recordInserted <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "分析记录写入失败");
            }

            // Record transaction
            UserTransaction transaction = new UserTransaction();
            transaction.setUserId(userId);
            transaction.setType("CONSUME");
            transaction.setAmount(-finalCost);
            transaction.setBalanceAfter(balanceAfter);
            transaction.setDescription("图片分析-" + finalModel);
            transaction.setReferenceId(record.getId());
            transaction.setSiteId(siteId);
            int txInserted = userTransactionMapper.insert(transaction);
            if (txInserted <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
            }

            return record;
        });

            // 6. Call API and Stream
            SseEmitter emitter = new SseEmitter(180000L); // 3 mins timeout
            Long recordSiteId = analysisRecord.getSiteId();
            Long recordId = analysisRecord.getId();

            try {
                // Build Body
                ObjectNode root = objectMapper.createObjectNode();
                root.put("model", actualModel);
                root.put("stream", true);

                ArrayNode messages = root.putArray("messages");

                // System Message
                if (chartProfile != null && !chartProfile.isEmpty()) {
                    ObjectNode systemMsg = messages.addObject();
                    systemMsg.put("role", "system");
                    systemMsg.put("content", chartProfile);
                }

                // User Message
                ObjectNode userMsg = messages.addObject();
                userMsg.put("role", "user");
                ArrayNode content = userMsg.putArray("content");

                // Text
                ObjectNode textContent = content.addObject();
                textContent.put("type", "text");
                textContent.put("text", request.getDirection() != null && !request.getDirection().isEmpty() ? request.getDirection() : "Analyze this image");

                // Image
                ObjectNode imageContent = content.addObject();
                imageContent.put("type", "image_url");
                ObjectNode imageUrlObj = imageContent.putObject("image_url");
                imageUrlObj.put("url", request.getImage());

                // Build Request
                String jsonBody = objectMapper.writeValueAsString(root);
                RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

                Request.Builder reqBuilder = new Request.Builder()
                        .url(apiInterface.getUrl())
                        .post(body);

                // Add Headers
                if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                    reqBuilder.addHeader("Authorization", "Bearer " + platform.getApiKey());
                }

                // Custom headers from interface config
                if (apiInterface.getHeaders() != null && !apiInterface.getHeaders().isEmpty()) {
                    try {
                        JsonNode headersNode = objectMapper.readTree(apiInterface.getHeaders());
                        if (headersNode.isArray()) {
                            for (JsonNode h : headersNode) {
                                if (h.has("key") && h.has("value")) {
                                    reqBuilder.addHeader(h.get("key").asText(), h.get("value").asText());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse headers", e);
                    }
                }

                okHttpClient.newCall(reqBuilder.build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        String errorMsg = resolveUnknownAnalysisError(e);
                        failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, errorMsg, "图片分析失败退款-" + finalModel);

                        sendBusinessErrorEvent(emitter, ErrorCode.API_CALL_FAILED.getCode(), errorMsg);
                        emitter.complete();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        StringBuilder fullResponse = new StringBuilder();
                        try (ResponseBody responseBody = response.body()) {
                            if (!response.isSuccessful()) {
                                String errorBody = responseBody != null ? responseBody.string() : "";
                                log.error("API Error: {} - {}", response.code(), errorBody);

                            String errorMsg = "系统繁忙，请稍后再试";
                            failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, errorMsg, "图片分析失败退款-" + finalModel);

                            sendBusinessErrorEvent(emitter, ErrorCode.API_CALL_FAILED.getCode(), errorMsg);
                                emitter.complete();
                                return;
                            }

                            if (responseBody == null) {
                            String errorMsg = "系统繁忙，请稍后再试";
                            failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, errorMsg, "图片分析失败退款-" + finalModel);
                            sendBusinessErrorEvent(emitter, ErrorCode.GENERATION_FAILED.getCode(), errorMsg);
                                emitter.complete();
                                return;
                            }

                            // Read stream manually with buffer to prevent blocking on readUtf8Line
                            okio.BufferedSource source = responseBody.source();
                            boolean isFinished = false;
                            
                            // Buffer for accumulating incomplete lines
                            StringBuilder lineBuffer = new StringBuilder();
                            byte[] buffer = new byte[8192];
                            
                            while (!source.exhausted()) {
                                int read = source.read(buffer);
                                if (read == -1) break;
                                
                                String chunk = new String(buffer, 0, read);
                                lineBuffer.append(chunk);
                                
                                // Process lines
                                int newlineIndex;
                                while ((newlineIndex = lineBuffer.indexOf("\n")) != -1) {
                                    String line = lineBuffer.substring(0, newlineIndex).trim();
                                    lineBuffer.delete(0, newlineIndex + 1);

                                    if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:") || line.startsWith("retry:")) {
                                        continue;
                                    }

                                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line;
                                    boolean completed = handleStreamPayload(payload, fullResponse, recordId, userId, recordSiteId, finalCost, finalModel, emitter);
                                    if (completed) {
                                        return;
                                    }
                                }
                            }
                            
                            // Process remaining buffer if any
                            if (lineBuffer.length() > 0) {
                                String line = lineBuffer.toString().trim();
                                if (!line.isEmpty() && !line.startsWith(":") && !line.startsWith("event:") && !line.startsWith("id:") && !line.startsWith("retry:")) {
                                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line;
                                    boolean completed = handleStreamPayload(payload, fullResponse, recordId, userId, recordSiteId, finalCost, finalModel, emitter);
                                    if (completed) {
                                        return;
                                    }
                                }
                            }
                            
                            // If stream ended but no [DONE] received, mark as success if we got content
                            if (!isFinished && fullResponse.length() > 0) {
                                markSuccessIfPending(recordId, recordSiteId, fullResponse.toString());
                            } else if (!isFinished) {
                                failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, "Stream ended without result", "图片分析失败退款-" + finalModel);
                                sendBusinessErrorEvent(emitter, ErrorCode.GENERATION_FAILED.getCode(), "Stream ended without result");
                            }
                            
                            emitter.complete();
                        } catch (Exception e) {
                            String errorMsg = resolveUnknownAnalysisError(e);
                            failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, errorMsg, "图片分析失败退款-" + finalModel);

                            sendBusinessErrorEvent(emitter, ErrorCode.GENERATION_FAILED.getCode(), errorMsg);
                            emitter.complete();
                        }
                    }
                });

            } catch (Exception e) {
                String errorMsg = resolveUnknownAnalysisError(e);
                failAndRefundIfPending(recordId, userId, recordSiteId, finalCost, errorMsg, "图片分析失败退款-" + finalModel);
                sendBusinessErrorEvent(emitter, ErrorCode.GENERATION_FAILED.getCode(), errorMsg);
                emitter.complete();
            }

            return emitter;
        }
}
