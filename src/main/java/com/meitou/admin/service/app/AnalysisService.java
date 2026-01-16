package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.dto.app.AnalysisResponse;
import com.meitou.admin.dto.app.ImageAnalysisRequest;
import com.meitou.admin.dto.app.VideoAnalysisRequest;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.entity.AnalysisRecord;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.entity.User;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图视分析服务类
 * 处理图片分析和视频分析的业务逻辑
 */
@Slf4j
@Service
public class AnalysisService {
    
    private final ApiPlatformService apiPlatformService;
    private final GenerationRecordMapper generationRecordMapper;
    private final AnalysisRecordMapper analysisRecordMapper;
    private final UserMapper userMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数，初始化RestTemplate并配置超时时间
     */
    public AnalysisService(ApiPlatformService apiPlatformService, 
                           GenerationRecordMapper generationRecordMapper,
                           AnalysisRecordMapper analysisRecordMapper,
                           UserMapper userMapper) {
        this.apiPlatformService = apiPlatformService;
        this.generationRecordMapper = generationRecordMapper;
        this.analysisRecordMapper = analysisRecordMapper;
        this.userMapper = userMapper;
        
        // 配置RestTemplate的超时时间
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 连接超时：10秒
        factory.setReadTimeout(60000); // 读取超时：60秒
        this.restTemplate = new RestTemplate(factory);
    }
    
    /**
     * 图片分析
     * 
     * @param request 图片分析请求
     * @param userId 用户ID
     * @return 分析响应
     */
    @Transactional
    public AnalysisResponse analyzeImage(ImageAnalysisRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 根据类型查找对应的API平台（type=image_analysis）
        ApiPlatform platform = findPlatformByType("image_analysis", null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(), "图片分析平台未配置，请在后台API接口管理中配置类型为'图片分析'的平台");
        }
        
        // 检查apiKey是否有效
        if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
            throw new BusinessException(ErrorCode.API_KEY_ERROR.getCode(), "API密钥未配置或解密失败，请重新设置API密钥");
        }
        
        // 获取图片分析接口配置
        ApiInterface analysisInterface = findAnalysisInterface(platform.getId());
        if (analysisInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "图片分析接口未配置");
        }
        
        try {
            // 构建请求参数
            Map<String, Object> apiRequest = buildImageAnalysisRequest(request, platform);
            
            // 调用API
            String responseJson = callApi(analysisInterface, platform, apiRequest);
            
            // 解析响应
            String result = parseAnalysisResult(responseJson, analysisInterface.getResponseMode());
            
            // 保存分析记录
            GenerationRecord record = new GenerationRecord();
            record.setUserId(userId);
            record.setUsername(user.getUsername());
            record.setType("image_analysis");
            record.setModel(request.getModel());
            record.setPrompt(request.getDirection() != null ? request.getDirection() : "图片分析");
            record.setStatus("success");
            record.setSiteId(SiteContext.getSiteId());
            record.setContentUrl(request.getImage());
            record.setCost(5); // 分析消耗5积分
            generationRecordMapper.insert(record);
            
            // 构建响应
            AnalysisResponse response = new AnalysisResponse();
            response.setResult(result);
            response.setStatus("success");
            
            return response;
            
        } catch (Exception e) {
            log.error("图片分析失败：{}", e.getMessage(), e);
            
            // 保存失败记录
            try {
                GenerationRecord record = new GenerationRecord();
                record.setUserId(userId);
                record.setUsername(user.getUsername());
                record.setType("image_analysis");
                record.setModel(request.getModel());
                record.setPrompt(request.getDirection() != null ? request.getDirection() : "图片分析");
                record.setStatus("failed");
                record.setSiteId(SiteContext.getSiteId());
                generationRecordMapper.insert(record);
            } catch (Exception ex) {
                log.error("保存失败记录时出错：{}", ex.getMessage());
            }
            
            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "图片分析失败：" + e.getMessage());
        }
    }
    
    /**
     * 视频分析
     * 
     * @param request 视频分析请求
     * @param userId 用户ID
     * @return 分析响应
     */
    @Transactional
    public AnalysisResponse analyzeVideo(VideoAnalysisRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 根据类型查找对应的API平台（type=video_analysis）
        ApiPlatform platform = findPlatformByType("video_analysis", null);
        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(), "视频分析平台未配置，请在后台API接口管理中配置类型为'视频分析'的平台");
        }
        
        // 检查apiKey是否有效
        if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
            throw new BusinessException(ErrorCode.API_KEY_ERROR.getCode(), "API密钥未配置或解密失败，请重新设置API密钥");
        }
        
        // 获取视频分析接口配置
        ApiInterface analysisInterface = findAnalysisInterface(platform.getId());
        if (analysisInterface == null) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "视频分析接口未配置");
        }
        
        // Save Analysis Record (Pending)
        AnalysisRecord analysisRecord = new AnalysisRecord();
        analysisRecord.setUserId(userId);
        analysisRecord.setType("video");
        analysisRecord.setContent(request.getVideo());
        analysisRecord.setStatus(0); // Pending
        analysisRecord.setSiteId(SiteContext.getSiteId());
        analysisRecordMapper.insert(analysisRecord);

        try {
            // 构建请求参数
            Map<String, Object> apiRequest = buildVideoAnalysisRequest(request, platform);
            
            // 调用API
            String responseJson = callApi(analysisInterface, platform, apiRequest);
            
            // 解析响应
            String result = parseAnalysisResult(responseJson, analysisInterface.getResponseMode());
            
            // Update Analysis Record (Success)
            analysisRecord.setStatus(1);
            analysisRecord.setResult(result);
            analysisRecordMapper.updateById(analysisRecord);

            // 保存分析记录
            GenerationRecord record = new GenerationRecord();
            record.setUserId(userId);
            record.setUsername(user.getUsername());
            record.setType("video_analysis");
            record.setModel(request.getModel());
            record.setPrompt(request.getDirection() != null ? request.getDirection() : "视频分析");
            record.setStatus("success");
            record.setSiteId(SiteContext.getSiteId());
            record.setContentUrl(request.getVideo());
            record.setCost(10); // 视频分析消耗10积分
            generationRecordMapper.insert(record);
            
            // 构建响应
            AnalysisResponse response = new AnalysisResponse();
            response.setResult(result);
            response.setStatus("success");
            
            return response;
            
        } catch (Exception e) {
            log.error("视频分析失败：{}", e.getMessage(), e);
            
            // Update Analysis Record (Failed)
            if (analysisRecord.getId() != null) {
                analysisRecord.setStatus(2);
                analysisRecord.setErrorMsg(e.getMessage());
                analysisRecordMapper.updateById(analysisRecord);
            }

            // 保存失败记录
            try {
                GenerationRecord record = new GenerationRecord();
                record.setUserId(userId);
                record.setUsername(user.getUsername());
                record.setType("video_analysis");
                record.setModel(request.getModel());
                record.setPrompt(request.getDirection() != null ? request.getDirection() : "视频分析");
                record.setStatus("failed");
                record.setSiteId(SiteContext.getSiteId());
                generationRecordMapper.insert(record);
            } catch (Exception ex) {
                log.error("保存失败记录时出错：{}", ex.getMessage());
            }
            
            throw new BusinessException(ErrorCode.GENERATION_FAILED.getCode(), "视频分析失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据类型查找API平台（apiKey已解密）
     */
    private ApiPlatform findPlatformByType(String type, Long siteId) {
        List<ApiPlatform> platforms = apiPlatformService.getPlatformsByTypeWithDecryptedKey(type, siteId);
        return platforms.isEmpty() ? null : platforms.get(0);
    }
    
    /**
     * 查找分析接口
     */
    private ApiInterface findAnalysisInterface(Long platformId) {
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platformId);
        // 查找第一个接口（分析接口通常只有一个）
        return interfaces.isEmpty() ? null : interfaces.get(0);
    }
    
    /**
     * 构建图片分析请求参数
     */
    private Map<String, Object> buildImageAnalysisRequest(ImageAnalysisRequest request, ApiPlatform platform) {
        Map<String, Object> params = new HashMap<>();
        params.put("image", request.getImage());
        if (request.getDirection() != null && !request.getDirection().isEmpty()) {
            params.put("direction", request.getDirection());
        }
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            params.put("model", request.getModel());
        }
        return params;
    }
    
    /**
     * 构建视频分析请求参数
     */
    private Map<String, Object> buildVideoAnalysisRequest(VideoAnalysisRequest request, ApiPlatform platform) {
        Map<String, Object> params = new HashMap<>();
        params.put("video", request.getVideo());
        if (request.getDirection() != null && !request.getDirection().isEmpty()) {
            params.put("direction", request.getDirection());
        }
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            params.put("model", request.getModel());
        }
        return params;
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
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty() && !headers.containsKey("Authorization")) {
                headers.set("Authorization", "Bearer " + platform.getApiKey());
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
                    String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("API调用失败，状态码：" + response.getStatusCode());
            }
            
            log.debug("API响应: {}", response.getBody());
            return response.getBody();
            
        } catch (Exception e) {
            log.error("调用API平台接口失败：{}", e.getMessage(), e);
            throw new RuntimeException("调用API平台接口失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 解析分析结果
     */
    private String parseAnalysisResult(String responseJson, String responseMode) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            
            // 尝试多种常见的响应格式
            if (root.has("result")) {
                return root.get("result").asText();
            }
            if (root.has("analysis")) {
                return root.get("analysis").asText();
            }
            if (root.has("text")) {
                return root.get("text").asText();
            }
            if (root.has("data") && root.get("data").has("result")) {
                return root.get("data").get("result").asText();
            }
            
            // 如果没有找到result字段，返回整个JSON的字符串形式
            return root.toString();
            
        } catch (Exception e) {
            log.error("解析分析结果失败：{}", e.getMessage());
            throw new RuntimeException("解析分析结果失败：" + e.getMessage());
        }
    }
}

