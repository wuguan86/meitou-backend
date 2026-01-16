package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.dto.app.VoiceCloneRequest;
import com.meitou.admin.dto.app.VoiceCloneResponse;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.common.SiteContext;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 声音克隆服务类
 * 处理声音克隆的业务逻辑
 */
@Slf4j
@Service
public class VoiceCloneService {
    
    private final ApiPlatformService apiPlatformService;
    private final GenerationRecordMapper generationRecordMapper;
    private final UserMapper userMapper;
    private final UserTransactionMapper userTransactionMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数，初始化RestTemplate并配置超时时间
     */
    public VoiceCloneService(ApiPlatformService apiPlatformService, 
                            GenerationRecordMapper generationRecordMapper,
                            UserMapper userMapper,
                            UserTransactionMapper userTransactionMapper) {
        this.apiPlatformService = apiPlatformService;
        this.generationRecordMapper = generationRecordMapper;
        this.userMapper = userMapper;
        this.userTransactionMapper = userTransactionMapper;
        
        // 配置RestTemplate的超时时间
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 连接超时：10秒
        factory.setReadTimeout(120000); // 读取超时：120秒
        this.restTemplate = new RestTemplate(factory);
    }
    
    /**
     * 声音克隆
     * 
     * @param request 声音克隆请求
     * @param userId 用户ID
     * @return 克隆响应
     */
    @Transactional
    public VoiceCloneResponse cloneVoice(VoiceCloneRequest request, Long userId) {
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int cost = 15;
        if (cost > 0) {
            int updatedRows = userMapper.deductBalance(userId, cost, LocalDateTime.now());
            if (updatedRows == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            User userAfter = userMapper.selectById(userId);
            int balanceAfter = (userAfter != null && userAfter.getBalance() != null) ? userAfter.getBalance() : 0;

            UserTransaction transaction = new UserTransaction();
            transaction.setUserId(userId);
            transaction.setType("CONSUME");
            transaction.setAmount(-cost);
            transaction.setBalanceAfter(balanceAfter);
            transaction.setDescription("声音克隆-" + (request.getModel() != null ? request.getModel() : "default"));
            transaction.setSiteId(SiteContext.getSiteId());
            userTransactionMapper.insert(transaction);
        }
        
        // 根据类型查找对应的API平台（type=voice_clone）
        ApiPlatform platform = findPlatformByType("voice_clone", null);
        if (platform == null) {
            throw new RuntimeException("声音克隆平台未配置，请在后台API接口管理中配置类型为'声音克隆'的平台");
        }
        
        // 检查apiKey是否有效
        if (platform.getApiKey() == null || platform.getApiKey().isEmpty()) {
            throw new RuntimeException("API密钥未配置或解密失败，请重新设置API密钥");
        }
        
        // 获取声音克隆接口配置
        ApiInterface voiceCloneInterface = findVoiceCloneInterface(platform.getId());
        if (voiceCloneInterface == null) {
            throw new RuntimeException("声音克隆接口未配置");
        }
        
        try {
            // 构建请求参数
            Map<String, Object> apiRequest = buildVoiceCloneRequest(request, platform);
            
            // 调用API
            String responseJson = callApi(voiceCloneInterface, platform, apiRequest);
            
            // 解析响应
            String audioUrl = parseAudioUrl(responseJson, voiceCloneInterface.getResponseMode());
            
            // 保存生成记录
            GenerationRecord record = new GenerationRecord();
            record.setUserId(userId);
            record.setUsername(user.getUsername());
            record.setType("voice_clone");
            record.setModel(request.getModel());
            record.setPrompt(request.getText());
            record.setStatus("success");
            record.setSiteId(SiteContext.getSiteId());
            record.setContentUrl(audioUrl);
            record.setCost(cost);
            generationRecordMapper.insert(record);
            
            // 构建响应
            VoiceCloneResponse response = new VoiceCloneResponse();
            response.setAudioUrl(audioUrl);
            response.setStatus("success");
            
            return response;
            
        } catch (Exception e) {
            log.error("声音克隆失败：{}", e.getMessage(), e);
            
            // 保存失败记录
            try {
                GenerationRecord record = new GenerationRecord();
                record.setUserId(userId);
                record.setUsername(user.getUsername());
                record.setType("voice_clone");
                record.setModel(request.getModel());
                record.setPrompt(request.getText());
                record.setStatus("failed");
                record.setSiteId(SiteContext.getSiteId());
                generationRecordMapper.insert(record);
            } catch (Exception ex) {
                log.error("保存失败记录时出错：{}", ex.getMessage());
            }
            
            throw new RuntimeException("声音克隆失败：" + e.getMessage());
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
     * 查找声音克隆接口
     */
    private ApiInterface findVoiceCloneInterface(Long platformId) {
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platformId);
        // 查找第一个接口
        return interfaces.isEmpty() ? null : interfaces.get(0);
    }
    
    /**
     * 构建声音克隆请求参数
     */
    private Map<String, Object> buildVoiceCloneRequest(VoiceCloneRequest request, ApiPlatform platform) {
        Map<String, Object> params = new HashMap<>();
        params.put("audio", request.getAudio());
        params.put("text", request.getText());
        if (request.getLanguage() != null && !request.getLanguage().isEmpty()) {
            params.put("language", request.getLanguage());
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
     * 解析音频URL
     */
    private String parseAudioUrl(String responseJson, String responseMode) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            
            // 尝试多种常见的响应格式
            if (root.has("audio_url")) {
                return root.get("audio_url").asText();
            }
            if (root.has("audioUrl")) {
                return root.get("audioUrl").asText();
            }
            if (root.has("url")) {
                return root.get("url").asText();
            }
            if (root.has("data") && root.get("data").has("url")) {
                return root.get("data").get("url").asText();
            }
            
            throw new RuntimeException("未找到生成的音频URL");
            
        } catch (Exception e) {
            log.error("解析音频URL失败：{}", e.getMessage());
            throw new RuntimeException("解析音频URL失败：" + e.getMessage());
        }
    }
}

