package com.meitou.admin.service.app;

import com.meitou.admin.dto.app.PromptOptimizeRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.dto.app.ImageGenerationResponse;
import com.meitou.admin.dto.app.ImageToImageRequest;
import com.meitou.admin.dto.app.TextToImageRequest;
import com.meitou.admin.dto.app.TextToVideoRequest;
import com.meitou.admin.dto.app.ImageToVideoRequest;
import com.meitou.admin.dto.app.VideoGenerationResponse;
import com.meitou.admin.entity.AnalysisRecord;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiParameterMapping;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.entity.User;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.service.common.ApiParameterMappingCacheService;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图片生成服务类
 * 处理文生图和图生图的业务逻辑，调用GRSAI API
 */
@Slf4j
@Service
public class GenerationService {

    private final ApiPlatformService apiPlatformService;
    private final GenerationRecordMapper generationRecordMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final ApiParameterMappingCacheService apiParameterMappingCacheService;
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;
    private final com.meitou.admin.service.common.AliyunOssService aliyunOssService;
    private final FileStorageService fileStorageService;

    private final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    private final RestTemplate restTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造函数，初始化RestTemplate并配置超时时间
     */
    public GenerationService(ApiPlatformService apiPlatformService,
            GenerationRecordMapper generationRecordMapper,
            AnalysisRecordMapper analysisRecordMapper,
            ApiParameterMappingCacheService apiParameterMappingCacheService,
            UserMapper userMapper,
            UserTransactionMapper userTransactionMapper,
            com.meitou.admin.service.common.AliyunOssService aliyunOssService,
            TransactionTemplate transactionTemplate,
            FileStorageService fileStorageService) {
        this.apiPlatformService = apiPlatformService;
        this.generationRecordMapper = generationRecordMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.apiParameterMappingCacheService = apiParameterMappingCacheService;
        this.userMapper = userMapper;
        this.userTransactionMapper = userTransactionMapper;
        this.aliyunOssService = aliyunOssService;
        this.transactionTemplate = transactionTemplate;
        this.fileStorageService = fileStorageService;

        // 配置RestTemplate的超时时间
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000); // 连接超时：30秒
        factory.setReadTimeout(300000); // 读取超时：300秒
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 文生图
     * 
     * @param request 文生图请求
     * @param userId  用户ID
     * @return 生成响应
     */
    public ImageGenerationResponse generateTextToImage(TextToImageRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 根据类型查找对应的API平台（type=txt2img）
        ApiPlatform platform = findPlatformByType("txt2img", request.getModel(), null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(),
                    "文生图平台未配置，或未找到支持该模型的平台");
        }

        // 检查apiKey是否有效（解密后不为null）
        if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
            throw new BusinessException(ErrorCode.API_KEY_ERROR);
        }

        // 获取文生图接口配置
        ApiInterface txt2imgInterface = findTextToImageInterface(platform.getId());
        if (txt2imgInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "文生图接口未配置");
        }

        // 计算消耗
        Integer cost = calculateCost(platform, request.getModel(), request.getResolution(), null, request.getQuantity(),
                "txt2img");

        // 阶段一：开启任务（扣费+记录）
        GenerationRecord record = startGenerationTask(userId, user.getUsername(), "txt2img", "image",
                request.getModel(), request.getPrompt(), cost, request);

        try {
            // 阶段二：调用API+上传OSS（无事务）
            // 构建请求参数
            Map<String, Object> apiRequest = buildTextToImageRequest(request, platform);

            // 应用参数映射（如果接口配置了参数映射）
            apiRequest = applyParameterMapping(apiRequest, txt2imgInterface);

            // 调用API
            String responseJson = callApi(txt2imgInterface, platform, apiRequest);

            // 检查是否为异步任务（webHook="-1"）
            if ("-1".equals(request.getWebHook())) {
                JsonNode root;
                if (responseJson != null && responseJson.trim().startsWith("data:")) {
                    root = parseFirstSseEvent(responseJson);
                } else {
                    root = objectMapper.readTree(responseJson);
                }

                String taskId = null;

                // 1. 直接在根节点找
                if (root.has("id")) {
                    taskId = root.get("id").asText();
                } else if (root.has("task_id")) {
                    taskId = root.get("task_id").asText();
                }
                // 2. 在data节点下找
                else if (root.has("data")) {
                    JsonNode data = root.get("data");
                    if (data.has("id")) {
                        taskId = data.get("id").asText();
                    } else if (data.has("task_id")) {
                        taskId = data.get("task_id").asText();
                    }
                }

                if (taskId != null && !taskId.isEmpty()) {
                    // 更新记录状态为processing
                    record.setStatus("processing");
                    // 将taskId存入generationParams
                    try {
                        Map<String, Object> params = new HashMap<>();
                        if (record.getGenerationParams() != null) {
                            params = objectMapper.readValue(record.getGenerationParams(), Map.class);
                        }
                        params.put("externalTaskId", taskId);
                        record.setGenerationParams(objectMapper.writeValueAsString(params));
                        record.setTaskId(taskId);
                        generationRecordMapper.updateById(record);
                    } catch (Exception e) {
                        log.warn("保存taskId失败: {}", e.getMessage());
                    }

                    ImageGenerationResponse response = new ImageGenerationResponse();
                    response.setGenerationRecordId(record.getId());
                    response.setTaskId(taskId);
                    response.setStatus("processing");
                    return response;
                } else {
                    log.warn("文生图异步请求(webHook=-1)未找到taskId，响应: {}", responseJson);
                }
            }

            // 解析响应（传递responseMode以支持不同格式）
            List<String> imageUrls = parseImageUrls(responseJson, txt2imgInterface.getResponseMode());

            // 上传图片到OSS并替换URL
            List<String> ossUrls = new ArrayList<>();
            for (String url : imageUrls) {
                // 如果已经是OSS链接（可能是API直接返回了OSS链接），则不重复上传
                if (url.contains("aliyuncs.com") || url.contains("myqcloud.com")) {
                    ossUrls.add(url);
                } else {
                    String ossUrl = aliyunOssService.uploadFromUrl(url, "images/");
                    ossUrls.add(ossUrl);
                }
            }
            imageUrls = ossUrls;

            // 阶段三：完成任务（更新记录并拆分）
            String thumbnailUrl = !imageUrls.isEmpty() ? imageUrls.get(0) : null;
            completeAndSplitGenerationTask(record.getId(), imageUrls, thumbnailUrl);

            // 构建响应
            ImageGenerationResponse response = new ImageGenerationResponse();
            // 签名URL
            List<String> signedUrls = imageUrls.stream()
                    .map(fileStorageService::getFileUrl)
                    .collect(Collectors.toList());
            response.setImageUrls(signedUrls);
            response.setGenerationRecordId(record.getId());
            response.setStatus("success");

            return response;

        } catch (Exception e) {
            log.error("文生图失败：{}", e.getMessage(), e);

            // 阶段三：失败处理（更新记录+退款）
            failGenerationTask(record, e.getMessage());

            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "文生图失败：" + e.getMessage());
        }
    }

    /**
     * 图生图
     * 
     * @param request 图生图请求
     * @param userId  用户ID
     * @return 生成响应
     */
    public ImageGenerationResponse generateImageToImage(ImageToImageRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 根据类型查找对应的API平台（type=img2img）
        ApiPlatform platform = findPlatformByType("img2img", request.getModel(), null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(),
                    "图生图平台未配置，或未找到支持该模型的平台");
        }

        // 检查apiKey是否有效（解密后不为null）
        if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
            throw new BusinessException(ErrorCode.API_KEY_ERROR);
        }

        // 获取图生图接口配置
        ApiInterface img2imgInterface = findImageToImageInterface(platform.getId());
        if (img2imgInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "图生图接口未配置");
        }

        // 计算消耗
        Integer cost = calculateCost(platform, request.getModel(), request.getResolution(), null, request.getQuantity(),
                "img2img");

        // 阶段一：开启任务（扣费+记录）
        GenerationRecord record = startGenerationTask(userId, user.getUsername(), "img2img", "image",
                request.getModel(), request.getPrompt(), cost, request);

        try {
            // 阶段二：调用API+上传OSS（无事务）
            // 构建请求参数
            Map<String, Object> apiRequest = buildImageToImageRequest(request, platform);

            // 应用参数映射（如果接口配置了参数映射）
            apiRequest = applyParameterMapping(apiRequest, img2imgInterface);

            // 调用API
            String responseJson = callApi(img2imgInterface, platform, apiRequest);

            // 检查是否为异步任务（webHook="-1"）
            if ("-1".equals(request.getWebHook())) {
                JsonNode root;
                if (responseJson != null && responseJson.trim().startsWith("data:")) {
                    root = parseFirstSseEvent(responseJson);
                } else {
                    root = objectMapper.readTree(responseJson);
                }

                String taskId = null;

                // 1. 直接在根节点找
                if (root.has("id")) {
                    taskId = root.get("id").asText();
                } else if (root.has("task_id")) {
                    taskId = root.get("task_id").asText();
                }
                // 2. 在data节点下找
                else if (root.has("data")) {
                    JsonNode data = root.get("data");
                    if (data.has("id")) {
                        taskId = data.get("id").asText();
                    } else if (data.has("task_id")) {
                        taskId = data.get("task_id").asText();
                    }
                }

                if (taskId != null && !taskId.isEmpty()) {
                    // 更新记录状态为processing
                    record.setStatus("processing");
                    // 将taskId存入generationParams
                    try {
                        Map<String, Object> params = new HashMap<>();
                        if (record.getGenerationParams() != null) {
                            params = objectMapper.readValue(record.getGenerationParams(), Map.class);
                        }
                        params.put("externalTaskId", taskId);
                        record.setGenerationParams(objectMapper.writeValueAsString(params));
                        record.setTaskId(taskId);
                        generationRecordMapper.updateById(record);
                    } catch (Exception e) {
                        log.warn("保存taskId失败: {}", e.getMessage());
                    }

                    ImageGenerationResponse response = new ImageGenerationResponse();
                    response.setGenerationRecordId(record.getId());
                    response.setTaskId(taskId);
                    response.setStatus("processing");
                    return response;
                } else {
                    log.warn("图生图异步请求(webHook=-1)未找到taskId，响应: {}", responseJson);
                }
            }

            // 解析响应（传递responseMode以支持不同格式）
            List<String> imageUrls = parseImageUrls(responseJson, img2imgInterface.getResponseMode());

            // 上传图片到OSS并替换URL
            List<String> ossUrls = new ArrayList<>();
            for (String url : imageUrls) {
                if (url.contains("aliyuncs.com") || url.contains("myqcloud.com")) {
                    ossUrls.add(url);
                } else {
                    String ossUrl = aliyunOssService.uploadFromUrl(url, "images/");
                    ossUrls.add(ossUrl);
                }
            }
            imageUrls = ossUrls;

            // 阶段三：完成任务（更新记录并拆分）
            String thumbnailUrl = !imageUrls.isEmpty() ? imageUrls.get(0) : null;
            completeAndSplitGenerationTask(record.getId(), imageUrls, thumbnailUrl);

            // 构建响应
            ImageGenerationResponse response = new ImageGenerationResponse();
            // 签名URL
            List<String> signedUrls = imageUrls.stream()
                    .map(fileStorageService::getFileUrl)
                    .collect(Collectors.toList());
            response.setImageUrls(signedUrls);
            response.setGenerationRecordId(record.getId());
            response.setStatus("success");
            return response;

        } catch (Exception e) {
            log.error("图生图失败：{}", e.getMessage(), e);

            // 阶段三：失败处理（更新记录+退款）
            failGenerationTask(record, e.getMessage());

            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "图生图失败：" + e.getMessage());
        }
    }

    /**
     * 文生视频
     */
    public VideoGenerationResponse generateTextToVideo(TextToVideoRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        ApiPlatform platform = findPlatformByType("txt2video", request.getModel(), null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(),
                    "文生视频平台未配置，或未找到支持该模型的平台");
        }

        ApiInterface txt2videoInterface = findTextToImageInterface(platform.getId()); // 复用
        if (txt2videoInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "文生视频接口未配置");
        }

        // 计算消耗
        Integer cost = calculateCost(platform, request.getModel(), request.getResolution(), request.getDuration(), 1,
                "txt2video");

        // 阶段一：开启任务（扣费+记录）
        GenerationRecord record = startGenerationTask(userId, user.getUsername(), "txt2video", "video",
                request.getModel(), request.getPrompt(), cost, request);

        try {
            // 阶段二：调用API+上传OSS（无事务）
            Map<String, Object> apiRequest = buildTextToVideoRequest(request, platform);
            String responseJson = callApi(txt2videoInterface, platform, apiRequest);

            // 检查是否为异步任务（webHook="-1"）
            if ("-1".equals(request.getWebHook())) {
                JsonNode root = objectMapper.readTree(responseJson);
                String taskId = null;

                // 1. 直接在根节点找
                if (root.has("id")) {
                    taskId = root.get("id").asText();
                } else if (root.has("task_id")) {
                    taskId = root.get("task_id").asText();
                }
                // 2. 在data节点下找
                else if (root.has("data")) {
                    JsonNode data = root.get("data");
                    if (data.has("id")) {
                        taskId = data.get("id").asText();
                    } else if (data.has("task_id")) {
                        taskId = data.get("task_id").asText();
                    }
                }

                if (taskId != null && !taskId.isEmpty()) {
                    // 更新记录状态为processing
                    record.setStatus("processing");
                    // 将taskId存入generationParams
                    try {
                        Map<String, Object> params = new HashMap<>();
                        if (record.getGenerationParams() != null) {
                            params = objectMapper.readValue(record.getGenerationParams(), Map.class);
                        }
                        params.put("externalTaskId", taskId);
                        record.setGenerationParams(objectMapper.writeValueAsString(params));
                        record.setTaskId(taskId);
                        generationRecordMapper.updateById(record);
                    } catch (Exception e) {
                        log.warn("保存taskId失败: {}", e.getMessage());
                    }

                    VideoGenerationResponse response = new VideoGenerationResponse();
                    response.setGenerationRecordId(record.getId());
                    response.setTaskId(taskId);
                    response.setStatus("processing");
                    return response;
                } else {
                    log.warn("文生视频异步请求(webHook=-1)未找到taskId，响应: {}", responseJson);
                }
            }

            String videoUrl = parseVideoUrl(responseJson);

            // Extract PID and Failure Reason
            String pid = null;
            String failureReason = null;
            try {
                JsonNode root = objectMapper.readTree(responseJson);
                pid = extractPidFromNode(root);
                failureReason = extractFailureReasonFromNode(root);
            } catch (Exception e) {
                log.warn("提取PID或失败原因失败", e);
            }

            // 上传视频到OSS
            String ossUrl;
            if (videoUrl.contains("aliyuncs.com") || videoUrl.contains("myqcloud.com")) {
                ossUrl = videoUrl;
            } else {
                ossUrl = aliyunOssService.uploadFromUrl(videoUrl, "videos/");
            }

            // 阶段三：完成任务（更新记录）
            // 假设使用了阿里云OSS，可以直接添加截帧参数作为缩略图
            String thumbnailUrl = null;
            if (ossUrl.contains("aliyuncs.com")) {
                thumbnailUrl = ossUrl + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
            }
            completeGenerationTask(record.getId(), ossUrl, thumbnailUrl, pid, failureReason);

            VideoGenerationResponse response = new VideoGenerationResponse();
            response.setVideoUrl(fileStorageService.getFileUrl(ossUrl));
            response.setGenerationRecordId(record.getId());
            response.setStatus("success");
            response.setPid(pid);
            response.setFailureReason(failureReason);
            return response;

        } catch (Exception e) {
            log.error("文生视频失败：{}", e.getMessage(), e);

            // 阶段三：失败处理（更新记录+退款）
            failGenerationTask(record, e.getMessage());

            String errorMsg = e.getMessage();
            if (errorMsg != null
                    && (errorMsg.contains("Unrecognized token") || errorMsg.contains("JsonParseException"))) {
                errorMsg = "API响应格式错误，请稍后重试";
            }

            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "文生视频失败：" + errorMsg);
        }
    }

    /**
     * 图生视频
     */
    public VideoGenerationResponse generateImageToVideo(ImageToVideoRequest request, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.getUrls() != null && request.getUrls().size() > 3) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "最多只能上传3张参考图片");
        }

        ApiPlatform platform = findPlatformByType("img2video", request.getModel(), null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(),
                    "图生视频平台未配置，或未找到支持该模型的平台");
        }

        ApiInterface img2videoInterface = findImageToImageInterface(platform.getId()); // 复用
        if (img2videoInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "图生视频接口未配置");
        }

        // 计算消耗
        Integer cost = calculateCost(platform, request.getModel(), request.getResolution(), request.getDuration(), 1,
                "img2video");

        // 阶段一：开启任务（扣费+记录）
        GenerationRecord record = startGenerationTask(userId, user.getUsername(), "img2video", "video",
                request.getModel(), request.getPrompt(), cost, request);

        try {
            // 阶段二：调用API+上传OSS（无事务）
            Map<String, Object> apiRequest = buildImageToVideoRequest(request, platform);
            String responseJson = callApi(img2videoInterface, platform, apiRequest);

            // 检查是否为异步任务（webHook="-1"）
            if ("-1".equals(request.getWebHook())) {
                JsonNode root = objectMapper.readTree(responseJson);
                String taskId = null;

                // 1. 直接在根节点找
                if (root.has("id")) {
                    taskId = root.get("id").asText();
                } else if (root.has("task_id")) {
                    taskId = root.get("task_id").asText();
                }
                // 2. 在data节点下找
                else if (root.has("data")) {
                    JsonNode data = root.get("data");
                    if (data.has("id")) {
                        taskId = data.get("id").asText();
                    } else if (data.has("task_id")) {
                        taskId = data.get("task_id").asText();
                    }
                }

                if (taskId != null && !taskId.isEmpty()) {
                    // 更新记录状态为processing
                    record.setStatus("processing");
                    // 将taskId存入generationParams
                    try {
                        Map<String, Object> params = new HashMap<>();
                        if (record.getGenerationParams() != null) {
                            params = objectMapper.readValue(record.getGenerationParams(), Map.class);
                        }
                        params.put("externalTaskId", taskId);
                        record.setGenerationParams(objectMapper.writeValueAsString(params));
                        record.setTaskId(taskId);
                        generationRecordMapper.updateById(record);
                    } catch (Exception e) {
                        log.warn("保存taskId失败: {}", e.getMessage());
                    }

                    VideoGenerationResponse response = new VideoGenerationResponse();
                    response.setGenerationRecordId(record.getId());
                    response.setTaskId(taskId);
                    response.setStatus("processing");
                    return response;
                } else {
                    log.warn("图生视频异步请求(webHook=-1)未找到taskId，响应: {}", responseJson);
                }
            }

            String videoUrl = parseVideoUrl(responseJson);

            // Extract PID and Failure Reason
            String pid = null;
            String failureReason = null;
            try {
                JsonNode root = objectMapper.readTree(responseJson);
                pid = extractPidFromNode(root);
                failureReason = extractFailureReasonFromNode(root);
            } catch (Exception e) {
                log.warn("提取PID或失败原因失败", e);
            }

            // 上传视频到OSS
            String ossUrl;
            if (videoUrl.contains("aliyuncs.com") || videoUrl.contains("myqcloud.com")) {
                ossUrl = videoUrl;
            } else {
                ossUrl = aliyunOssService.uploadFromUrl(videoUrl, "videos/");
            }

            // 阶段三：完成任务（更新记录）
            // 设置缩略图为参考图，或者OSS截帧
            String thumbnailUrl = null;
            if (request.getImage() != null) {
                thumbnailUrl = request.getImage();
            } else if (ossUrl.contains("aliyuncs.com")) {
                thumbnailUrl = ossUrl + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
            }
            completeGenerationTask(record.getId(), ossUrl, thumbnailUrl, pid, failureReason);

            VideoGenerationResponse response = new VideoGenerationResponse();
            response.setVideoUrl(fileStorageService.getFileUrl(ossUrl));
            response.setGenerationRecordId(record.getId());
            response.setStatus("success");
            response.setPid(pid);
            response.setFailureReason(failureReason);
            return response;

        } catch (Exception e) {
            log.error("图生视频失败：{}", e.getMessage(), e);

            // 阶段三：失败处理（更新记录+退款）
            failGenerationTask(record, e.getMessage());

            String errorMsg = e.getMessage();
            if (errorMsg != null
                    && (errorMsg.contains("Unrecognized token") || errorMsg.contains("JsonParseException"))) {
                errorMsg = "API响应格式错误，请稍后重试";
            }

            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "图生视频失败：" + errorMsg);
        }
    }

    /**
     * 获取用户生成记录
     * 
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页数量
     * @param type   类型筛选 (可选)
     * @return 记录分页
     */
    public Page<GenerationRecord> getUserGenerationRecords(Long userId, int page, int size, String type) {
        Page<GenerationRecord> pageParam = new Page<>(page, size);
        QueryWrapper<GenerationRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            // 前端传的是 'image' 或 'video'，数据库里存的是 'txt2img', 'img2img' (image) 或 'txt2video',
            // 'img2video' (video)
            // 或者使用新加的 file_type 字段
            if ("image".equals(type)) {
                queryWrapper.eq("file_type", "image");
            } else if ("video".equals(type)) {
                queryWrapper.eq("file_type", "video");
            } else {
                // 如果是其他具体类型
                queryWrapper.eq("type", type);
            }
        }
        queryWrapper.orderByDesc("created_at");
        Page<GenerationRecord> result = generationRecordMapper.selectPage(pageParam, queryWrapper);

        // 处理签名URL
        if (result.getRecords() != null) {
            result.getRecords().forEach(record -> {
                // 修复视频缺少缩略图的问题
                if (("video".equals(record.getFileType()) || "video".equals(record.getType())
                        || "txt2video".equals(record.getType()) || "img2video".equals(record.getType()))
                        && (record.getThumbnailUrl() == null || record.getThumbnailUrl().isEmpty())) {
                    String url = record.getContentUrl();
                    if (url != null && url.contains("aliyuncs.com") && !url.contains("?")) {
                        // 阿里云OSS视频截帧参数
                        record.setThumbnailUrl(url + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast");
                    }
                }

                record.setContentUrl(fileStorageService.getFileUrl(record.getContentUrl()));
                record.setThumbnailUrl(fileStorageService.getFileUrl(record.getThumbnailUrl()));
            });
        }

        return result;
    }

    /**
     * 获取单个生成记录详情
     *
     * @param id     记录ID
     * @param userId 用户ID
     * @return 记录详情
     */
    public GenerationRecord getGenerationRecord(Long id, Long userId) {
        GenerationRecord record = generationRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权查看此记录");
        }

        // 处理签名URL
        record.setContentUrl(fileStorageService.getFileUrl(record.getContentUrl()));
        if (record.getThumbnailUrl() != null) {
            record.setThumbnailUrl(fileStorageService.getFileUrl(record.getThumbnailUrl()));
        }

        return record;
    }

    /**
     * 删除生成记录
     *
     * @param id     记录ID
     * @param userId 用户ID
     */
    public void deleteGenerationRecord(Long id, Long userId) {
        GenerationRecord record = generationRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权删除此记录");
        }
        generationRecordMapper.deleteById(id);
    }

    /**
     * 发布生成记录
     *
     * @param id     记录ID
     * @param userId 用户ID
     */
    public void publishGenerationRecord(Long id, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        GenerationRecord record = generationRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权操作此记录");
        }
        record.setIsPublish("1");
        int updated = generationRecordMapper.updateById(record);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "发布状态更新失败");
        }
    }

    private Map<String, Object> buildTextToVideoRequest(TextToVideoRequest request, ApiPlatform platform) {
        return applyParameterMappings(new HashMap<>(), request, platform.getId(), request.getModel());
    }

    private Map<String, Object> buildImageToVideoRequest(ImageToVideoRequest request, ApiPlatform platform) {
        Map<String, Object> params = applyParameterMappings(new HashMap<>(), request, platform.getId(),
                request.getModel());
        if (request.getRemixTargetId() != null && !request.getRemixTargetId().isEmpty()) {
            params.put("remixTargetId", request.getRemixTargetId());
        }
        return params;
    }

    private String parseVideoUrl(String responseJson) {
        try {
            // 自动检测响应格式：如果响应以 "data: " 开头，则视为SSE格式
            if (responseJson != null && responseJson.trim().startsWith("data:")) {
                return parseVideoSseResponse(responseJson);
            }

            JsonNode root = objectMapper.readTree(responseJson);
            if (root.has("video_url"))
                return root.get("video_url").asText();
            if (root.has("url"))
                return root.get("url").asText();

            // Check data object
            if (root.has("data")) {
                JsonNode data = root.get("data");
                if (data.has("url"))
                    return data.get("url").asText();
                if (data.has("video_url"))
                    return data.get("video_url").asText();

                // Check results array inside data
                if (data.has("results") && data.get("results").isArray()) {
                    JsonNode results = data.get("results");
                    if (results.size() > 0) {
                        JsonNode first = results.get(0);
                        if (first.has("url"))
                            return first.get("url").asText();
                        if (first.has("video_url"))
                            return first.get("video_url").asText();
                    }
                }
            }

            // 尝试通用解析
            if (root.has("results") && root.get("results").isArray()) {
                JsonNode results = root.get("results");
                if (results.size() > 0) {
                    JsonNode first = results.get(0);
                    if (first.has("url"))
                        return first.get("url").asText();
                    if (first.has("video_url"))
                        return first.get("video_url").asText();
                }
            }

            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "无法解析视频URL");
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "解析视频响应失败: " + e.getMessage());
        }
    }

    /**
     * 解析视频SSE格式的流式响应
     */
    private String parseVideoSseResponse(String sseResponse) {
        String videoUrl = null;
        JsonNode lastDataNode = null;
        String lastStatus = null;

        try {
            // 按行分割SSE数据
            String[] lines = sseResponse.split("\n");

            for (String line : lines) {
                line = line.trim();
                // 跳过空行
                if (line.isEmpty()) {
                    continue;
                }

                // 检查是否是data行
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6); // 移除 "data: " 前缀

                    try {
                        // 解析JSON数据
                        JsonNode dataNode = objectMapper.readTree(jsonData);
                        lastDataNode = dataNode;

                        // 检查状态字段
                        if (dataNode.has("status")) {
                            lastStatus = dataNode.get("status").asText();
                        }

                        // 尝试提取视频URL
                        String url = extractVideoUrlFromNode(dataNode);
                        if (url != null && !url.isEmpty()) {
                            videoUrl = url;
                        }

                    } catch (Exception e) {
                        // 忽略解析失败的行，继续处理下一行
                        log.debug("解析SSE数据行失败：{}", e.getMessage());
                    }
                }
            }

            // 如果已经找到URL，直接返回
            if (videoUrl != null) {
                return videoUrl;
            }

            // 如果没有找到视频URL，但最后一个状态是completed/success/succeeded，尝试从最后一个数据节点提取
            if (lastDataNode != null &&
                    ("completed".equals(lastStatus) || "success".equals(lastStatus)
                            || "succeeded".equals(lastStatus))) {
                String url = extractVideoUrlFromNode(lastDataNode);
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }

            // 如果还是没有找到，检查是否有错误信息
            if (lastDataNode != null) {
                if (lastDataNode.has("error") && !lastDataNode.get("error").asText().isEmpty()) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                            "API返回错误：" + lastDataNode.get("error").asText());
                }
                if (lastDataNode.has("failure_reason") && !lastDataNode.get("failure_reason").asText().isEmpty()) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                            "API返回失败原因：" + lastDataNode.get("failure_reason").asText());
                }
                // 如果状态是running，但没有URL
                if ("running".equals(lastStatus)) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                            "视频生成任务仍在处理中（状态：running），未返回结果URL");
                }
            }

            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "未找到生成的视频URL，响应状态：" + lastStatus);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析SSE响应失败：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "解析SSE响应失败：" + e.getMessage());
        }
    }

    /**
     * 解析SSE响应的第一条数据
     */
    private JsonNode parseFirstSseEvent(String sseResponse) {
        try {
            String[] lines = sseResponse.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6);
                    return objectMapper.readTree(jsonData);
                }
            }
        } catch (Exception e) {
            log.warn("解析SSE第一条数据失败: {}", e.getMessage());
        }
        return objectMapper.createObjectNode();
    }

    /**
     * 从JsonNode中提取视频URL
     */
    private String extractVideoUrlFromNode(JsonNode node) {
        if (node.has("video_url") && !node.get("video_url").asText().isEmpty())
            return node.get("video_url").asText();
        if (node.has("url") && !node.get("url").asText().isEmpty())
            return node.get("url").asText();

        if (node.has("data")) {
            JsonNode data = node.get("data");
            if (data.has("url") && !data.get("url").asText().isEmpty())
                return data.get("url").asText();
            if (data.has("video_url") && !data.get("video_url").asText().isEmpty())
                return data.get("video_url").asText();

            if (data.has("results") && data.get("results").isArray()) {
                JsonNode results = data.get("results");
                if (results.size() > 0) {
                    JsonNode first = results.get(0);
                    if (first.has("url"))
                        return first.get("url").asText();
                    if (first.has("video_url"))
                        return first.get("video_url").asText();
                }
            }
        }

        if (node.has("results") && node.get("results").isArray()) {
            JsonNode results = node.get("results");
            if (results.size() > 0) {
                JsonNode first = results.get(0);
                if (first.has("url"))
                    return first.get("url").asText();
                if (first.has("video_url"))
                    return first.get("video_url").asText();
            }
        }
        return null;
    }

    /**
     * 从JsonNode中提取PID
     */
    private String extractPidFromNode(JsonNode node) {
        if (node.has("results") && node.get("results").isArray()) {
            JsonNode results = node.get("results");
            if (results.size() > 0) {
                JsonNode first = results.get(0);
                if (first.has("pid"))
                    return first.get("pid").asText();
            }
        }

        if (node.has("data")) {
            JsonNode data = node.get("data");
            if (data.has("results") && data.get("results").isArray()) {
                JsonNode results = data.get("results");
                if (results.size() > 0) {
                    JsonNode first = results.get(0);
                    if (first.has("pid"))
                        return first.get("pid").asText();
                }
            }
        }

        return null;
    }

    /**
     * 从JsonNode中提取失败原因
     */
    private String extractFailureReasonFromNode(JsonNode node) {
        if (node.has("failure_reason") && !node.get("failure_reason").isNull()) {
            String reason = node.get("failure_reason").asText();
            if (reason != null && !reason.isEmpty())
                return reason;
        }

        if (node.has("error") && !node.get("error").isNull()) {
            String error = node.get("error").asText();
            if (error != null && !error.isEmpty())
                return error;
        }

        return null;
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

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * 开启生成任务（事务：扣费+记录+流水）
     */
    private GenerationRecord startGenerationTask(Long userId, String username, String type, String fileType,
            String model, String prompt, Integer cost, Object requestParams) {
        return transactionTemplate.execute(status -> {
            if (userId == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "userId不能为空");
            }
            if (cost == null || cost < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "扣费配置异常");
            }
            Long siteId = SiteContext.getSiteId();
            if (siteId == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "站点信息缺失");
            }

            if (cost > 0) {
                int updateRows = userMapper.deductBalance(userId, cost, LocalDateTime.now());
                if (updateRows == 0) {
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

            GenerationRecord record = new GenerationRecord();
            record.setUserId(userId);
            record.setUsername(username);
            record.setType(type);
            record.setModel(model);
            record.setPrompt(prompt);
            record.setStatus("processing");
            record.setSiteId(siteId);
            record.setFileType(fileType);
            record.setCost(cost);
            try {
                record.setGenerationParams(objectMapper.writeValueAsString(requestParams));
            } catch (Exception e) {
                log.warn("序列化生成参数失败", e);
            }
            generationRecordMapper.insert(record);

            // 记录流水
            UserTransaction transaction = new UserTransaction();
            transaction.setUserId(userId);
            transaction.setType("CONSUME");
            transaction.setAmount(-cost);
            transaction.setBalanceAfter(balanceAfter);
            transaction.setReferenceId(record.getId());
            transaction.setDescription("AI生成消耗: " + type);
            transaction.setSiteId(siteId);
            int inserted = userTransactionMapper.insert(transaction);
            if (inserted <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "交易流水写入失败");
            }

            return record;
        });
    }

    /**
     * 完成生成任务并拆分记录（事务：更新记录+创建新记录）
     * 用于处理一次生成多张图片的情况，将每张图片存为一条独立的记录
     */
    private void completeAndSplitGenerationTask(Long recordId, List<String> contentUrls, String firstThumbnailUrl) {
        transactionTemplate.execute(status -> {
            if (contentUrls == null || contentUrls.isEmpty()) {
                return null;
            }

            GenerationRecord originalRecord = generationRecordMapper.selectById(recordId);
            if (originalRecord == null) {
                return null;
            }

            UpdateWrapper<GenerationRecord> updateOriginal = new UpdateWrapper<>();
            updateOriginal.eq("id", recordId);
            updateOriginal.eq("status", "processing");
            updateOriginal.set("status", "success");
            updateOriginal.set("content_url", contentUrls.get(0));
            updateOriginal.set("thumbnail_url", firstThumbnailUrl != null ? firstThumbnailUrl : contentUrls.get(0));
            updateOriginal.set("updated_at", LocalDateTime.now());

            int updatedRows = generationRecordMapper.update(null, updateOriginal);
            if (updatedRows == 0) {
                return null;
            }

            for (int i = 1; i < contentUrls.size(); i++) {
                String url = contentUrls.get(i);
                GenerationRecord newRecord = new GenerationRecord();
                newRecord.setUserId(originalRecord.getUserId());
                newRecord.setUsername(originalRecord.getUsername());
                newRecord.setSiteId(originalRecord.getSiteId());
                newRecord.setType(originalRecord.getType());
                newRecord.setFileType(originalRecord.getFileType());
                newRecord.setModel(originalRecord.getModel());
                newRecord.setPrompt(originalRecord.getPrompt());
                newRecord.setGenerationParams(originalRecord.getGenerationParams());
                newRecord.setTaskId(originalRecord.getTaskId());
                newRecord.setPid(originalRecord.getPid());
                newRecord.setCost(0);
                newRecord.setStatus("success");
                newRecord.setContentUrl(url);
                newRecord.setThumbnailUrl(url);

                generationRecordMapper.insert(newRecord);
            }

            return null;
        });
    }

    private void completeGenerationTask(Long recordId, String contentUrl, String thumbnailUrl, String pid,
            String failureReason) {
        transactionTemplate.execute(status -> {
            UpdateWrapper<GenerationRecord> update = new UpdateWrapper<>();
            update.eq("id", recordId);
            update.eq("status", "processing");
            update.set("status", "success");
            update.set("content_url", contentUrl);
            if (thumbnailUrl != null) {
                update.set("thumbnail_url", thumbnailUrl);
            }
            if (pid != null) {
                update.set("pid", pid);
            }
            if (failureReason != null) {
                update.set("failure_reason", failureReason);
            }
            update.set("updated_at", LocalDateTime.now());

            generationRecordMapper.update(null, update);
            return null;
        });
    }

    /**
     * 失败处理（事务：更新记录+退款+流水）
     */
    private void failGenerationTask(GenerationRecord record, String failureReason) {
        if (record == null || record.getId() == null) {
            return;
        }
        Long siteId = record.getSiteId();
        if (siteId == null) {
            return;
        }
        String safeReason = truncate(failureReason, 500);
        runWithSiteContext(siteId, () -> transactionTemplate.execute(status -> {
            UpdateWrapper<GenerationRecord> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", record.getId());
            updateWrapper.eq("status", "processing");
            updateWrapper.set("status", "failed");
            if (safeReason != null) {
                updateWrapper.set("failure_reason", safeReason);
            }
            updateWrapper.set("updated_at", LocalDateTime.now());
            int updatedRows = generationRecordMapper.update(null, updateWrapper);
            if (updatedRows == 0) {
                return null;
            }

            Integer cost = record.getCost();
            if (cost == null || cost <= 0) {
                return null;
            }

            Long userId = record.getUserId();
            if (userId == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "退款用户缺失");
            }

            int balanceUpdated = userMapper.incrementBalance(userId, cost, LocalDateTime.now());
            if (balanceUpdated == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "退款失败");
            }

            User userAfter = userMapper.selectById(userId);
            int balanceAfter = (userAfter != null && userAfter.getBalance() != null) ? userAfter.getBalance() : 0;

            UserTransaction transaction = new UserTransaction();
            transaction.setUserId(userId);
            transaction.setType("REFUND");
            transaction.setAmount(cost);
            transaction.setBalanceAfter(balanceAfter);
            transaction.setReferenceId(record.getId());
            transaction.setDescription("任务失败退款: " + (record.getType() != null ? record.getType() : ""));
            transaction.setSiteId(siteId);
            int inserted = userTransactionMapper.insert(transaction);
            if (inserted <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "退款流水写入失败");
            }
            return null;
        }));
    }

    public void failIfProcessingAndRefund(Long recordId, String failureReason) {
        GenerationRecord record = generationRecordMapper.selectById(recordId);
        if (record == null) {
            return;
        }
        failGenerationTask(record, failureReason);
    }

    /**
     * 提示词优化
     */
    public SseEmitter optimizePrompt(PromptOptimizeRequest request, Long userId) {
        // 1. 查找平台
        ApiPlatform platform = findPlatformByType("prompt_optimize", request.getModel(), null);
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
        analysisRecordMapper.insert(analysisRecord);

        // 3. 构建请求
        SseEmitter emitter = new SseEmitter(60000L); // 1 minute timeout

        try {
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
                    analysisRecord.setErrorMsg(e.getMessage());
                    analysisRecordMapper.updateById(analysisRecord);

                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ex) {
                        log.error("Error completing emitter", ex);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    StringBuilder fullResponse = new StringBuilder();
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful()) {
                            // Update Analysis Record (Failed)
                            analysisRecord.setStatus(2);
                            analysisRecord.setErrorMsg("Request failed: " + response.code());
                            analysisRecordMapper.updateById(analysisRecord);

                            emitter.send(SseEmitter.event().name("error").data("Request failed: " + response.code()));
                            emitter.complete();
                            return;
                        }

                        if (responseBody == null) {
                            emitter.complete();
                            return;
                        }

                        // Stream the response
                        okio.BufferedSource source = responseBody.source();
                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line != null && !line.isEmpty()) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    if ("[DONE]".equals(data.trim())) {
                                        // Update Analysis Record (Success)
                                        analysisRecord.setStatus(1);
                                        analysisRecord.setResult(fullResponse.toString());
                                        analysisRecordMapper.updateById(analysisRecord);

                                        continue;
                                    }
                                    fullResponse.append(data);
                                    emitter.send(data);
                                }
                            }
                        }
                        emitter.complete();
                    } catch (Exception e) {
                        // Update Analysis Record (Failed)
                        analysisRecord.setStatus(2);
                        analysisRecord.setErrorMsg(e.getMessage());
                        analysisRecordMapper.updateById(analysisRecord);

                        emitter.completeWithError(e);
                    }
                }
            });

        } catch (Exception e) {
            // Update Analysis Record (Failed)
            analysisRecord.setStatus(2);
            analysisRecord.setErrorMsg(e.getMessage());
            analysisRecordMapper.updateById(analysisRecord);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 根据类型和模型查找API平台（apiKey已解密）
     * 
     * @param type   API类型：image_analysis, video_analysis, txt2img, img2img,
     *               txt2video, img2video, voice_clone
     * @param model  模型名称
     * @param siteId 站点ID（可选）
     * @return 找到的匹配平台，如果没有则返回null
     */
    private ApiPlatform findPlatformByType(String type, String model, Long siteId) {
        // 使用根据类型和模型查询的方法
        return apiPlatformService.getPlatformByTypeAndModel(type, model, siteId);
    }

    /**
     * 查找文生图接口
     */
    private ApiInterface findTextToImageInterface(Long platformId) {
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platformId);
        // 查找responseMode为JSON或Stream的接口（非Result类型）
        // 注意：Stream类型也支持，因为API可能返回SSE格式的流式数据
        return interfaces.stream()
                .filter(i -> i.getResponseMode() != null && !"Result".equals(i.getResponseMode()))
                .findFirst()
                .orElse(interfaces.isEmpty() ? null : interfaces.get(0)); // 如果没有找到，返回第一个
    }

    /**
     * 查找图生图接口
     */
    private ApiInterface findImageToImageInterface(Long platformId) {
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platformId);
        // 查找responseMode为JSON的接口
        return interfaces.stream()
                .filter(i -> i.getResponseMode() != null && "JSON".equals(i.getResponseMode()))
                .findFirst()
                .orElse(interfaces.isEmpty() ? null : interfaces.get(0));
    }

    /**
     * 应用参数映射
     */
    private Map<String, Object> applyParameterMappings(Map<String, Object> currentParams, Object requestDto,
            Long platformId, String model) {
        // 从缓存获取映射配置（已排序：通用在前，特定模型在后）
        List<ApiParameterMapping> mappings = apiParameterMappingCacheService.getMappings(platformId, model);

        if (mappings == null || mappings.isEmpty()) {
            return currentParams;
        }

        // 准备上下文（包含当前参数和DTO字段）
        Map<String, Object> context = new HashMap<>(currentParams);
        addDtoToContext(context, requestDto);

        // 应用映射
        Map<String, Object> newParams = new HashMap<>(currentParams);

        for (ApiParameterMapping m : mappings) {
            String target = m.getTargetParam();
            if (target == null || target.isEmpty())
                continue;

            if (m.getMappingType() != null && m.getMappingType() == 2) {
                // 固定值
                Object val = m.getFixedValue();
                String type = m.getParamType();
                if ("boolean".equalsIgnoreCase(type)) {
                    val = Boolean.parseBoolean(m.getFixedValue());
                } else if ("integer".equalsIgnoreCase(type)) {
                    try {
                        val = Integer.parseInt(m.getFixedValue());
                    } catch (NumberFormatException e) {
                        // ignore, keep string
                    }
                }
                newParams.put(target, val);
            } else {
                // 字段映射
                String internal = m.getInternalParam();
                if (internal != null && context.containsKey(internal)) {
                    newParams.put(target, context.get(internal));
                    // 如果映射了内部参数，且该参数存在于原参数表中，则移除原参数（重命名效果）
                    if (currentParams.containsKey(internal)) {
                        newParams.remove(internal);
                    }
                }
            }
        }

        return newParams;
    }

    private void addDtoToContext(Map<String, Object> context, Object dto) {
        if (dto == null)
            return;

        String resolution = null;
        String aspectRatio = null;
        Integer quantity = null;

        if (dto instanceof TextToImageRequest) {
            TextToImageRequest r = (TextToImageRequest) dto;
            context.putIfAbsent("prompt", r.getPrompt());
            context.putIfAbsent("model", r.getModel());
            context.putIfAbsent("aspect_ratio", r.getAspectRatio());
            context.putIfAbsent("aspectRatio", r.getAspectRatio());
            context.putIfAbsent("resolution", r.getResolution());
            context.putIfAbsent("quantity", r.getQuantity());
            context.putIfAbsent("webHook", r.getWebHook());
            context.putIfAbsent("shutProgress", r.getShutProgress());

            resolution = r.getResolution();
            aspectRatio = r.getAspectRatio();
            quantity = r.getQuantity();
        } else if (dto instanceof ImageToImageRequest) {
            ImageToImageRequest r = (ImageToImageRequest) dto;
            context.putIfAbsent("prompt", r.getPrompt());
            context.putIfAbsent("model", r.getModel());
            context.putIfAbsent("aspect_ratio", r.getAspectRatio());
            context.putIfAbsent("aspectRatio", r.getAspectRatio());
            context.putIfAbsent("resolution", r.getResolution());
            context.putIfAbsent("quantity", r.getQuantity());

            // 将urls放入上下文
            if (r.getUrls() != null && !r.getUrls().isEmpty()) {
                context.putIfAbsent("urls", r.getUrls());
                // 为了兼容旧的参数映射配置，同时填充image, image2, image3
                context.putIfAbsent("image", r.getUrls().get(0));
                if (r.getUrls().size() > 1)
                    context.putIfAbsent("image2", r.getUrls().get(1));
                if (r.getUrls().size() > 2)
                    context.putIfAbsent("image3", r.getUrls().get(2));
            }

            resolution = r.getResolution();
            aspectRatio = r.getAspectRatio();
            quantity = r.getQuantity();
        } else if (dto instanceof TextToVideoRequest) {
            TextToVideoRequest r = (TextToVideoRequest) dto;
            context.putIfAbsent("prompt", r.getPrompt());
            context.putIfAbsent("model", r.getModel());
            context.putIfAbsent("aspect_ratio", r.getAspectRatio());
            context.putIfAbsent("aspectRatio", r.getAspectRatio());
            context.putIfAbsent("resolution", r.getResolution());
            context.putIfAbsent("duration", r.getDuration());
            context.putIfAbsent("firstFrameUrl", r.getFirstFrameUrl());
            context.putIfAbsent("lastFrameUrl", r.getLastFrameUrl());
            context.putIfAbsent("urls", r.getUrls());

            resolution = r.getResolution();
            aspectRatio = r.getAspectRatio();
        } else if (dto instanceof ImageToVideoRequest) {
            ImageToVideoRequest r = (ImageToVideoRequest) dto;
            context.putIfAbsent("prompt", r.getPrompt());
            context.putIfAbsent("model", r.getModel());
            context.putIfAbsent("aspect_ratio", r.getAspectRatio());
            context.putIfAbsent("aspectRatio", r.getAspectRatio());
            context.putIfAbsent("resolution", r.getResolution());
            context.putIfAbsent("duration", r.getDuration());
            context.putIfAbsent("image", r.getImage());
            context.putIfAbsent("firstFrameUrl", r.getFirstFrameUrl());
            context.putIfAbsent("lastFrameUrl", r.getLastFrameUrl());
            context.putIfAbsent("urls", r.getUrls());
            context.putIfAbsent("remixTargetId", r.getRemixTargetId());
            context.putIfAbsent("webHook", r.getWebHook());
            context.putIfAbsent("shutProgress", r.getShutProgress());
            context.putIfAbsent("characters", r.getCharacters());
            resolution = r.getResolution();
            aspectRatio = r.getAspectRatio();
        }

        // 通用计算逻辑
        if (resolution != null && !resolution.isEmpty()) {
            Map<String, Integer> size = parseResolution(resolution, aspectRatio);
            context.putIfAbsent("width", size.get("width"));
            context.putIfAbsent("height", size.get("height"));
        }

        if (quantity != null) {
            context.putIfAbsent("n", quantity);
            context.putIfAbsent("variants", quantity);
        }

        if (aspectRatio != null && !aspectRatio.isEmpty()) {
            context.putIfAbsent("size", aspectRatio);
        }
    }

    /**
     * 构建文生图请求参数
     */
    private Map<String, Object> buildTextToImageRequest(TextToImageRequest request, ApiPlatform platform) {
        Map<String, Object> params = applyParameterMappings(new HashMap<>(), request, platform.getId(),
                request.getModel());

        // 确保webHook和shutProgress被传递
        if (request.getWebHook() != null && !request.getWebHook().isEmpty()) {
            params.putIfAbsent("webHook", request.getWebHook());
        }
        if (request.getShutProgress() != null) {
            params.putIfAbsent("shutProgress", request.getShutProgress());
        }

        return params;
    }

    /**
     * 构建图生图请求参数
     */
    private Map<String, Object> buildImageToImageRequest(ImageToImageRequest request, ApiPlatform platform) {
        return applyParameterMappings(new HashMap<>(), request, platform.getId(), request.getModel());
    }

    /**
     * 解析分辨率
     */
    private Map<String, Integer> parseResolution(String resolution, String aspectRatio) {
        Map<String, Integer> size = new HashMap<>();

        // 根据分辨率设置基础尺寸
        int baseSize = 1024; // 默认1K
        if ("2K".equals(resolution)) {
            baseSize = 2048;
        } else if ("4K".equals(resolution)) {
            baseSize = 4096;
        }

        // 根据宽高比调整
        if (aspectRatio != null && !aspectRatio.isEmpty() && !"Auto".equals(aspectRatio)) {
            String[] parts = aspectRatio.split(":");
            if (parts.length == 2) {
                try {
                    double ratio = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
                    if (ratio > 1) {
                        // 横向
                        size.put("width", baseSize);
                        size.put("height", (int) (baseSize / ratio));
                    } else {
                        // 纵向
                        size.put("width", (int) (baseSize * ratio));
                        size.put("height", baseSize);
                    }
                } catch (NumberFormatException e) {
                    size.put("width", baseSize);
                    size.put("height", baseSize);
                }
            } else {
                size.put("width", baseSize);
                size.put("height", baseSize);
            }
        } else {
            size.put("width", baseSize);
            size.put("height", baseSize);
        }

        return size;
    }

    /**
     * 应用参数映射（根据接口配置的parametersJson映射参数名）
     * 如果接口配置了参数映射，则将内部参数名映射为API接口期望的参数名
     * 
     * @param params       原始参数Map
     * @param apiInterface API接口配置
     * @return 映射后的参数Map
     */
    private Map<String, Object> applyParameterMapping(Map<String, Object> params, ApiInterface apiInterface) {
        // 如果接口没有配置参数映射，直接返回原参数
        if (apiInterface.getParametersJson() == null || apiInterface.getParametersJson().trim().isEmpty()) {
            return params;
        }

        try {
            // 解析参数映射配置（JSON格式：{"image": "reference_image", "prompt": "text"}）
            JsonNode mappingNode = objectMapper.readTree(apiInterface.getParametersJson());
            Map<String, Object> mappedParams = new HashMap<>();

            // 遍历原始参数，应用映射
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String internalKey = entry.getKey();
                Object value = entry.getValue();

                // 如果配置中有映射，使用映射后的键名；否则使用原键名
                if (mappingNode.has(internalKey)) {
                    String mappedKey = mappingNode.get(internalKey).asText();
                    mappedParams.put(mappedKey, value);
                    log.debug("参数映射: {} -> {}", internalKey, mappedKey);
                } else {
                    // 没有映射配置，保持原键名
                    mappedParams.put(internalKey, value);
                }
            }

            return mappedParams;
        } catch (Exception e) {
            log.warn("解析参数映射失败，使用原始参数：{}", e.getMessage());
            return params; // 如果解析失败，返回原始参数
        }
    }

    /**
     * 调用API平台接口
     */
    private String callApi(ApiInterface apiInterface, ApiPlatform platform, Map<String, Object> requestParams) {
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 添加Authorization头（从接口配置的headers中解析）
            if (apiInterface.getHeaders() != null && !apiInterface.getHeaders().isEmpty()) {
                try {
                    JsonNode headersNode = objectMapper.readTree(apiInterface.getHeaders());
                    headersNode.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        String value = entry.getValue().asText();
                        // 替换{apiKey}占位符
                        if (value.contains("{apiKey}") && platform.getApiKey() != null) {
                            value = value.replace("{apiKey}", platform.getApiKey());
                        }
                        headers.set(key, value);
                    });
                } catch (Exception e) {
                    log.warn("解析headers失败，使用默认配置：{}", e.getMessage());
                }
            }

            // 如果有API Key，默认添加Authorization头
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                // 记录API密钥的前几位用于调试（不记录完整密钥）
                String apiKeyPreview = platform.getApiKey().length() > 10
                        ? platform.getApiKey().substring(0, 10) + "***"
                        : "***";
                log.debug("使用API Key: {} (长度: {})", apiKeyPreview, platform.getApiKey().length());
                headers.set("Authorization", "Bearer " + platform.getApiKey());
            } else if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
                log.warn("API密钥为空，平台: {}", platform.getName());
            }

            // 构建请求体
            String requestBody = objectMapper.writeValueAsString(requestParams);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            log.info("调用API平台接口: {} {} (平台: {})", apiInterface.getMethod(), apiInterface.getUrl(), platform.getName());
            log.debug("请求参数: {}", requestBody);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiInterface.getUrl(),
                    HttpMethod.valueOf(apiInterface.getMethod()),
                    entity,
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.API_CALL_FAILED.getCode(),
                        "API调用失败，状态码：" + response.getStatusCode());
            }

            log.debug("API响应: {}", response.getBody());
            return response.getBody();

        } catch (Exception e) {
            log.error("调用API平台接口失败：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.API_CALL_FAILED.getCode(), "调用API平台接口失败：" + e.getMessage());
        }
    }

    /**
     * 解析图片URL列表
     * 
     * @param responseJson 响应JSON字符串
     * @param responseMode 响应模式：JSON、Stream、Result
     */
    private List<String> parseImageUrls(String responseJson, String responseMode) {
        List<String> imageUrls = new ArrayList<>();

        try {
            // 自动检测响应格式：如果响应以 "data: " 开头，则视为SSE格式
            boolean isSseFormat = responseJson != null && responseJson.trim().startsWith("data:");

            // 如果是Stream模式或自动检测到SSE格式，解析SSE格式的流式数据
            if ("Stream".equals(responseMode) || isSseFormat) {
                return parseSseResponse(responseJson);
            }

            // 普通JSON格式
            JsonNode root = objectMapper.readTree(responseJson);

            // 首先检查响应是否包含错误信息
            // 格式1: { "code": -1, "msg": "错误消息" }
            if (root.has("code")) {
                int code = root.get("code").asInt();
                if (code != 0 && code != 200) {
                    // 非成功状态码，提取错误消息
                    String errorMsg = "API调用失败";
                    if (root.has("msg")) {
                        errorMsg = root.get("msg").asText();
                    } else if (root.has("message")) {
                        errorMsg = root.get("message").asText();
                    } else if (root.has("error")) {
                        errorMsg = root.get("error").asText();
                    }

                    // 针对常见错误提供更友好的提示
                    String friendlyMsg = errorMsg;
                    if (errorMsg != null && errorMsg.toLowerCase().contains("apikey")) {
                        friendlyMsg = "API密钥错误，请检查后台API接口管理中的API密钥配置是否正确";
                    } else if (errorMsg != null && errorMsg.toLowerCase().contains("unauthorized")) {
                        friendlyMsg = "API认证失败，请检查API密钥是否有效";
                    } else if (errorMsg != null && errorMsg.toLowerCase().contains("forbidden")) {
                        friendlyMsg = "API访问被拒绝，请检查API密钥权限";
                    }

                    log.error("API返回错误响应: code={}, msg={}", code, errorMsg);
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), friendlyMsg);
                }
            }
            // 格式2: { "error": "错误消息" }
            else if (root.has("error")) {
                String errorMsg = root.get("error").asText();
                throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "API返回错误：" + errorMsg);
            }
            // 格式3: { "status": "error", "message": "错误消息" }
            else if (root.has("status")) {
                String status = root.get("status").asText();
                if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                    String errorMsg = "API调用失败";
                    if (root.has("message")) {
                        errorMsg = root.get("message").asText();
                    } else if (root.has("msg")) {
                        errorMsg = root.get("msg").asText();
                    }
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "API返回错误：" + errorMsg);
                }
            }

            // 尝试多种常见的响应格式
            // 格式1: { "results": [{ "url": "url1", "width": 1024, "height": 1024 }, ...] }
            // (新API格式)
            if (root.has("results") && root.get("results").isArray()) {
                for (JsonNode result : root.get("results")) {
                    if (result.has("url")) {
                        imageUrls.add(result.get("url").asText());
                    }
                }
            }
            // 格式2: { "url": "url1" } (旧格式，兼容)
            else if (root.has("url") && !root.has("results")) {
                imageUrls.add(root.get("url").asText());
            }
            // 格式3: { "images": ["url1", "url2"] }
            else if (root.has("images") && root.get("images").isArray()) {
                for (JsonNode img : root.get("images")) {
                    if (img.isTextual()) {
                        imageUrls.add(img.asText());
                    } else if (img.has("url")) {
                        imageUrls.add(img.get("url").asText());
                    }
                }
            }
            // 格式4: data 字段处理
            else if (root.has("data")) {
                JsonNode data = root.get("data");
                // data 是数组
                if (data.isArray()) {
                    for (JsonNode item : data) {
                        if (item.has("url")) {
                            imageUrls.add(item.get("url").asText());
                        }
                    }
                }
                // data 是对象 (新增支持: { "data": { "results": [...] } })
                else if (data.isObject()) {
                    if (data.has("results") && data.get("results").isArray()) {
                        for (JsonNode item : data.get("results")) {
                            if (item.has("url")) {
                                imageUrls.add(item.get("url").asText());
                            }
                        }
                    } else if (data.has("url")) {
                        imageUrls.add(data.get("url").asText());
                    }
                }
            }
            // 格式5: { "result": { "images": ["url1", "url2"] } }
            else if (root.has("result")) {
                JsonNode result = root.get("result");
                if (result.has("images") && result.get("images").isArray()) {
                    for (JsonNode img : result.get("images")) {
                        if (img.isTextual()) {
                            imageUrls.add(img.asText());
                        }
                    }
                }
            }
            // 格式6: 直接是base64数据 { "image": "data:image/..." }
            else if (root.has("image")) {
                imageUrls.add(root.get("image").asText());
            }

        } catch (RuntimeException e) {
            // 重新抛出运行时异常（包括我们检查到的错误）
            throw e;
        } catch (Exception e) {
            log.error("解析API响应失败：{}", e.getMessage(), e);
            throw new RuntimeException("解析API响应失败：" + e.getMessage());
        }

        if (imageUrls.isEmpty()) {
            throw new RuntimeException("未找到生成的图片URL，请检查API响应格式是否正确");
        }

        return imageUrls;
    }

    /**
     * 解析SSE格式的流式响应
     * SSE格式：每行以 "data: " 开头，后面跟着JSON对象
     * 
     * @param sseResponse SSE格式的响应字符串
     * @return 图片URL列表
     */
    private List<String> parseSseResponse(String sseResponse) {
        List<String> imageUrls = new ArrayList<>();
        JsonNode lastDataNode = null;
        String lastStatus = null;

        try {
            // 按行分割SSE数据
            String[] lines = sseResponse.split("\n");

            for (String line : lines) {
                line = line.trim();
                // 跳过空行
                if (line.isEmpty()) {
                    continue;
                }

                // 检查是否是data行
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6); // 移除 "data: " 前缀

                    try {
                        // 解析JSON数据
                        JsonNode dataNode = objectMapper.readTree(jsonData);
                        lastDataNode = dataNode;

                        // 检查状态字段
                        if (dataNode.has("status")) {
                            lastStatus = dataNode.get("status").asText();

                            // 如果状态是completed、success或succeeded，尝试提取图片URL
                            if ("completed".equals(lastStatus) || "success".equals(lastStatus)
                                    || "succeeded".equals(lastStatus)) {
                                // 尝试从results字段提取图片URL
                                if (dataNode.has("results") && dataNode.get("results").isArray()) {
                                    for (JsonNode result : dataNode.get("results")) {
                                        if (result.has("url")) {
                                            imageUrls.add(result.get("url").asText());
                                        } else if (result.isTextual()) {
                                            imageUrls.add(result.asText());
                                        }
                                    }
                                }
                                // 或者直接从url字段提取
                                else if (dataNode.has("url") && !dataNode.get("url").asText().isEmpty()) {
                                    imageUrls.add(dataNode.get("url").asText());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略解析失败的行，继续处理下一行
                        log.debug("解析SSE数据行失败：{}", e.getMessage());
                    }
                }
            }

            // 如果没有找到图片URL，但最后一个状态是completed/success/succeeded，尝试从最后一个数据节点提取
            if (imageUrls.isEmpty() && lastDataNode != null &&
                    ("completed".equals(lastStatus) || "success".equals(lastStatus)
                            || "succeeded".equals(lastStatus))) {
                if (lastDataNode.has("results") && lastDataNode.get("results").isArray()) {
                    for (JsonNode result : lastDataNode.get("results")) {
                        if (result.has("url")) {
                            imageUrls.add(result.get("url").asText());
                        } else if (result.isTextual()) {
                            imageUrls.add(result.asText());
                        }
                    }
                } else if (lastDataNode.has("url") && !lastDataNode.get("url").asText().isEmpty()) {
                    imageUrls.add(lastDataNode.get("url").asText());
                }
            }

            // 如果还是没有找到，检查是否有错误信息
            if (imageUrls.isEmpty() && lastDataNode != null) {
                if (lastDataNode.has("error") && !lastDataNode.get("error").asText().isEmpty()) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                            "API返回错误：" + lastDataNode.get("error").asText());
                }
                if (lastDataNode.has("failure_reason") && !lastDataNode.get("failure_reason").asText().isEmpty()) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                            "API返回失败原因：" + lastDataNode.get("failure_reason").asText());
                }
                // 如果状态是running，尝试从当前数据中提取URL（有些API会在running状态时也返回部分结果）
                if ("running".equals(lastStatus) && lastDataNode != null) {
                    // 尝试从results字段提取（即使状态是running，也可能有部分结果）
                    if (lastDataNode.has("results") && lastDataNode.get("results").isArray()) {
                        for (JsonNode result : lastDataNode.get("results")) {
                            if (result.has("url") && !result.get("url").asText().isEmpty()) {
                                imageUrls.add(result.get("url").asText());
                            } else if (result.isTextual() && !result.asText().isEmpty()) {
                                imageUrls.add(result.asText());
                            }
                        }
                    }
                    // 如果还是没有找到URL，抛出异常
                    if (imageUrls.isEmpty()) {
                        throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(),
                                "图片生成任务仍在处理中（状态：running），请稍后查询结果。如需实时获取结果，建议使用轮询或Webhook方式");
                    }
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析SSE响应失败：{}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "解析SSE响应失败：" + e.getMessage());
        }

        if (imageUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "未找到生成的图片URL，响应状态：" + lastStatus);
        }

        return imageUrls;
    }

    /**
     * 视频响应信息内部类
     */
    private static class VideoResponseInfo {
        private String taskId; // 任务ID
        private String videoUrl; // 视频URL
        private String status; // 状态：running, succeeded, failed
        private Integer progress; // 进度：0~100
        private String failureReason; // 失败原因
        private String errorMessage; // 错误信息

        // Getters and Setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getVideoUrl() {
            return videoUrl;
        }

        public void setVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getProgress() {
            return progress;
        }

        public void setProgress(Integer progress) {
            this.progress = progress;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 计算消耗积分
     */
    private Integer calculateCost(ApiPlatform platform, String model, String resolution, Integer duration,
            Integer quantity, String type) {
        // 简单计算：文生图每个10积分，图生图每个15积分，视频每个20积分
        int baseCost;
        if ("txt2img".equals(type)) {
            baseCost = 10;
        } else if ("img2img".equals(type)) {
            baseCost = 15;
        } else if ("txt2video".equals(type) || "img2video".equals(type)) {
            baseCost = 20;
        } else {
            baseCost = 10; // 默认
        }

        // 尝试从平台配置的模型信息中获取更精确的消耗配置
        if (platform != null && platform.getSupportedModels() != null && !platform.getSupportedModels().isEmpty()) {
            try {
                // 尝试解析为JSON数组
                if (platform.getSupportedModels().trim().startsWith("[")) {
                    JsonNode models = objectMapper.readTree(platform.getSupportedModels());
                    for (JsonNode m : models) {
                        if (m.has("name") && m.get("name").asText().equals(model)) {
                            // 找到匹配的模型配置

                            // 1. 检查是否有特殊计费规则 (costRules)
                            if (m.has("costRules") && m.get("costRules").isArray()) {
                                for (JsonNode rule : m.get("costRules")) {
                                    boolean match = true;

                                    // 检查分辨率匹配 (如果规则指定了分辨率)
                                    if (rule.has("resolution") && !rule.get("resolution").asText().isEmpty()) {
                                        if (resolution == null || !resolution.equals(rule.get("resolution").asText())) {
                                            match = false;
                                        }
                                    }

                                    // 检查时长匹配 (如果规则指定了时长)
                                    if (match && rule.has("duration") && rule.get("duration").asInt() > 0) {
                                        if (duration == null || duration.intValue() != rule.get("duration").asInt()) {
                                            match = false;
                                        }
                                    }

                                    // 如果规则匹配，返回配置的消耗
                                    if (match && rule.has("cost")) {
                                        int ruleCost = rule.get("cost").asInt();
                                        if (ruleCost >= 0) {
                                            return ruleCost * (quantity != null ? quantity : 1);
                                        }
                                    }
                                }
                            }

                            // 2. 使用模型默认消耗
                            if (m.has("defaultCost")) {
                                int modelDefault = m.get("defaultCost").asInt();
                                if (modelDefault >= 0) {
                                    return modelDefault * (quantity != null ? quantity : 1);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析平台模型配置失败，使用默认计费规则: {}", e.getMessage());
            }
        }

        return baseCost * (quantity != null ? quantity : 1);
    }

    /**
     * 查询任务状态
     */
    public ImageGenerationResponse getTaskStatus(Long recordId) {
        GenerationRecord record = generationRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        ImageGenerationResponse response = new ImageGenerationResponse();
        response.setGenerationRecordId(recordId);
        response.setStatus(record.getStatus());
        response.setPid(record.getPid());

        if ("success".equals(record.getStatus())) {
            if (record.getContentUrl() != null) {
                if (record.getType() != null && record.getType().contains("video")) {
                    response.setVideoUrl(fileStorageService.getFileUrl(record.getContentUrl()));
                } else {
                    List<String> urls = Arrays.asList(record.getContentUrl().split(","));
                    List<String> signedUrls = urls.stream()
                            .map(fileStorageService::getFileUrl)
                            .collect(Collectors.toList());
                    response.setImageUrls(signedUrls);
                }
            }
            response.setProgress(100);
            return response;
        } else if ("failed".equals(record.getStatus())) {
            response.setErrorMessage("生成失败");
            return response;
        }

        // Processing, check external
        if ("processing".equals(record.getStatus())) {
            try {
                String externalTaskId = record.getTaskId();

                if (externalTaskId == null || externalTaskId.trim().isEmpty()) {
                    String rawParams = record.getGenerationParams();
                    if (rawParams != null && !rawParams.trim().isEmpty()) {
                        JsonNode params = objectMapper.readTree(rawParams);
                        if (params.has("externalTaskId"))
                            externalTaskId = params.get("externalTaskId").asText();
                        else if (params.has("taskId"))
                            externalTaskId = params.get("taskId").asText();
                    }
                }

                if ((externalTaskId == null || externalTaskId.trim().isEmpty()) && record.getPid() != null
                        && !record.getPid().trim().isEmpty()) {
                    externalTaskId = record.getPid().trim();
                }

                if (externalTaskId != null && !externalTaskId.trim().isEmpty()) {
                    response.setTaskId(externalTaskId);

                    ApiPlatform platform = findPlatformByType(record.getType(), record.getModel(), null);
                    if (platform != null) {
                        ApiInterface apiInterface = null;
                        if ("txt2img".equals(record.getType()) || "txt2video".equals(record.getType())) {
                            apiInterface = findTextToImageInterface(platform.getId());
                        } else if ("img2img".equals(record.getType()) || "img2video".equals(record.getType())) {
                            apiInterface = findImageToImageInterface(platform.getId());
                        }

                        if (apiInterface != null) {
                            String submitUrl = apiInterface.getUrl();
                            String statusJson = null;

                            // 优先检查是否需要使用 POST /v1/draw/result 接口 (统一结果查询接口)
                            // 针对 Grsai/Dakka 等平台，无论是文生图(/v1/draw/)还是图生视频(/v1/video/)，都统一使用 /v1/draw/result
                            // 查询
                            boolean isUnifiedResultApi = false;
                            String fetchUrl = null;

                            if (submitUrl != null) {
                                if (submitUrl.contains("/v1/draw/")) {
                                    int drawIndex = submitUrl.indexOf("/v1/draw/");
                                    String baseUrl = submitUrl.substring(0, drawIndex + 9); // include /v1/draw/
                                    fetchUrl = baseUrl + "result";
                                    isUnifiedResultApi = true;
                                } else if (submitUrl.contains("/v1/video/") && submitUrl.contains("dakka.com.cn")) {
                                    // 针对 dakka.com.cn 的视频生成，也使用 /v1/draw/result
                                    // https://grsai.dakka.com.cn/v1/video/veo ->
                                    // https://grsai.dakka.com.cn/v1/draw/result
                                    int v1Index = submitUrl.indexOf("/v1/");
                                    if (v1Index > 0) {
                                        String baseUrl = submitUrl.substring(0, v1Index);
                                        fetchUrl = baseUrl + "/v1/draw/result";
                                        isUnifiedResultApi = true;
                                    }
                                }
                            }

                            if (isUnifiedResultApi && fetchUrl != null) {
                                try {
                                    statusJson = callPostResultApi(fetchUrl, externalTaskId, platform, apiInterface);
                                } catch (Exception e) {
                                    log.warn("POST查询任务状态失败，尝试降级到GET推导URL: {}", e.getMessage());
                                }
                            }

                            if (statusJson == null) {
                                String deducedUrl = deduceFetchUrl(apiInterface.getUrl(), externalTaskId);
                                statusJson = callGetApi(deducedUrl, platform, apiInterface);
                            }

                            JsonNode root = objectMapper.readTree(statusJson);

                            // 兼容 data 包装层
                            JsonNode dataNode = root;
                            if (root.has("data") && root.get("data").isObject()) {
                                dataNode = root.get("data");
                            }

                            String status = null;
                            if (dataNode.has("status"))
                                status = dataNode.get("status").asText();

                            if (dataNode.has("progress")) {
                                String p = dataNode.get("progress").asText();
                                response.setProgress(parseProgress(p));
                            }

                            if ("SUCCESS".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status)) {
                                if (record.getType() != null && record.getType().contains("video")) {
                                    String videoUrl = parseVideoUrl(statusJson);
                                    String ossUrl;
                                    if (videoUrl.contains("aliyuncs.com") || videoUrl.contains("myqcloud.com")) {
                                        ossUrl = videoUrl;
                                    } else {
                                        try {
                                            ossUrl = aliyunOssService.uploadFromUrl(videoUrl, "videos/");
                                        } catch (Exception e) {
                                            ossUrl = videoUrl;
                                            log.warn("视频上传OSS失败: {}", e.getMessage());
                                        }
                                    }

                                    // 视频缩略图逻辑
                                    String thumbnailUrl = null;
                                    if (ossUrl.contains("aliyuncs.com")) {
                                        thumbnailUrl = ossUrl
                                                + "?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
                                    }

                                    // 尝试提取PID
                                    String pid = extractPidFromNode(root);

                                    completeGenerationTask(record.getId(), ossUrl, thumbnailUrl, pid, null);
                                    response.setVideoUrl(fileStorageService.getFileUrl(ossUrl));
                                    response.setStatus("success");
                                    response.setPid(pid);
                                    response.setProgress(100);

                                } else {
                                    List<String> imageUrls = parseImageUrls(statusJson, apiInterface.getResponseMode());
                                    if (!imageUrls.isEmpty()) {
                                        List<String> ossUrls = new ArrayList<>();
                                        for (String url : imageUrls) {
                                            if (url.contains("aliyuncs.com") || url.contains("myqcloud.com")) {
                                                ossUrls.add(url);
                                            } else {
                                                try {
                                                    String ossUrl = aliyunOssService.uploadFromUrl(url, "images/");
                                                    ossUrls.add(ossUrl);
                                                } catch (Exception e) {
                                                    ossUrls.add(url);
                                                }
                                            }
                                        }

                                        String thumbnailUrl = !ossUrls.isEmpty() ? ossUrls.get(0) : null;
                                        completeAndSplitGenerationTask(record.getId(), ossUrls, thumbnailUrl);

                                        List<String> signedUrls = ossUrls.stream()
                                                .map(fileStorageService::getFileUrl)
                                                .collect(Collectors.toList());
                                        response.setImageUrls(signedUrls);
                                        response.setStatus("success");
                                        response.setProgress(100);
                                    }
                                }
                            } else if ("FAILED".equalsIgnoreCase(status) || "FAILURE".equalsIgnoreCase(status)) {
                                String reason = "Unknown error";
                                if (dataNode.has("failure_reason")) {
                                    reason = dataNode.get("failure_reason").asText();
                                } else if (dataNode.has("fail_reason")) {
                                    reason = dataNode.get("fail_reason").asText();
                                }

                                if (dataNode.has("error")) {
                                    reason += ": " + dataNode.get("error").asText();
                                }

                                failGenerationTask(record, reason);
                                response.setStatus("failed");
                                response.setErrorMessage(reason);
                            } else if ("RUNNING".equalsIgnoreCase(status)) {
                                response.setStatus("processing");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询任务状态失败: {}", e.getMessage());
                // 如果是404，说明任务可能不存在或URL错误，直接标记为失败，避免前端无限轮询
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    // 确保退款和更新状态
                    failGenerationTask(record, e.getMessage());

                    response.setStatus("failed");
                    response.setErrorMessage("查询任务失败: 任务不存在或已过期 (404)");
                }
            }
        }

        return response;
    }

    private String deduceFetchUrl(String submitUrl, String taskId) {
        if (submitUrl.contains("/submit/")) {
            return submitUrl.replaceAll("/submit/.*", "/task/" + taskId + "/fetch");
        }

        // 针对dakka.com.cn的特殊处理
        if (submitUrl.contains("dakka.com.cn")) {
            if (submitUrl.contains("/draw/")) {
                // 假设查询接口为 /draw/task/{taskId} 或 /task/{taskId}
                // 这里尝试构造 /draw/task/{taskId}
                // 例如: https://qrsai.dakka.com.cn/v1/draw/nano-banana ->
                // https://qrsai.dakka.com.cn/v1/draw/task/{taskId}
                int drawIndex = submitUrl.indexOf("/draw/");
                if (drawIndex > 0) {
                    String baseUrl = submitUrl.substring(0, drawIndex + 6); // include "/draw/"
                    return baseUrl + "task/" + taskId;
                }
            } else if (submitUrl.contains("/video/")) {
                // 假设视频任务查询也是类似结构
                // .../v1/video/veo -> .../v1/video/task/{taskId}
                int videoIndex = submitUrl.indexOf("/video/");
                if (videoIndex > 0) {
                    String baseUrl = submitUrl.substring(0, videoIndex + 7); // include "/video/"
                    return baseUrl + "task/" + taskId;
                }
            }
        }

        return submitUrl + "/" + taskId;
    }

    private String callGetApi(String url, ApiPlatform platform, ApiInterface apiInterface) {
        HttpHeaders headers = new HttpHeaders();
        if (apiInterface.getHeaders() != null && !apiInterface.getHeaders().isEmpty()) {
            try {
                Map<String, String> headerMap = objectMapper.readValue(apiInterface.getHeaders(), Map.class);
                headerMap.forEach(headers::set);
            } catch (Exception e) {
                log.warn("解析headers失败", e);
            }
        }

        if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
            headers.set("Authorization", "Bearer " + platform.getApiKey());
        }

        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }

    /**
     * 获取用户的生成记录（分页）
     *
     * @param page   页码
     * @param size   每页数量
     * @param userId 用户ID
     * @param type   类型筛选（可选）
     * @return 分页结果
     */
    public Page<GenerationRecord> getGenerationRecords(Integer page, Integer size, Long userId, String type) {
        Page<GenerationRecord> pageParam = new Page<>(page, size);
        QueryWrapper<GenerationRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);

        if (type != null && !type.isEmpty() && !"all".equals(type)) {
            wrapper.eq("file_type", type);
        }

        wrapper.orderByDesc("created_at");

        Page<GenerationRecord> result = generationRecordMapper.selectPage(pageParam, wrapper);

        // 处理签名URL
        if (result.getRecords() != null && !result.getRecords().isEmpty()) {
            for (GenerationRecord record : result.getRecords()) {
                record.setContentUrl(fileStorageService.getFileUrl(record.getContentUrl()));
                record.setThumbnailUrl(fileStorageService.getFileUrl(record.getThumbnailUrl()));
                // record.setReferenceImage(fileStorageService.getFileUrl(record.getReferenceImage()));
                // // GenerationRecord entity doesn't have this field
            }
        }

        return result;
    }

    private Integer parseProgress(String p) {
        if (p == null)
            return 0;
        p = p.replace("%", "");
        try {
            return Integer.parseInt(p);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String callPostResultApi(String url, String taskId, ApiPlatform platform, ApiInterface apiInterface) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (apiInterface.getHeaders() != null && !apiInterface.getHeaders().isEmpty()) {
            try {
                Map<String, String> headerMap = objectMapper.readValue(apiInterface.getHeaders(), Map.class);
                headerMap.forEach(headers::set);
            } catch (Exception e) {
                log.warn("解析headers失败", e);
            }
        }

        if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
            headers.set("Authorization", "Bearer " + platform.getApiKey());
        }

        Map<String, String> body = new HashMap<>();
        body.put("id", taskId);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("构建请求失败: " + e.getMessage());
        }
    }
}
