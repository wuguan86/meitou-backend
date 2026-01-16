package com.meitou.admin.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.app.SaveCharacterRequest;
import com.meitou.admin.entity.*;
import com.meitou.admin.entity.Character;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.mapper.CharacterMapper;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 角色服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final Pattern TIMESTAMPS_PATTERN = Pattern.compile("^\\d+,\\d+$");

    private final CharacterMapper characterMapper;
    private final GenerationRecordMapper generationRecordMapper;
    private final ApiPlatformService apiPlatformService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    /**
     * 保存角色视频
     *
     * @param request 请求参数
     * @param userId  用户ID
     * @return 保存结果
     */
    @Transactional
    public Result<Map<String, Object>> saveCharacterVideo(SaveCharacterRequest request, Long userId) {
        // 1. 校验参数
        if (request.getPid() == null || request.getPid().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "PID不能为空");
        }
        if (request.getTimestamps() == null || request.getTimestamps().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "时间戳不能为空");
        }
        validateTimestamps(request.getTimestamps().trim());

        // 2. 查询生成记录
        LambdaQueryWrapper<GenerationRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(GenerationRecord::getPid, request.getPid().trim());
        recordWrapper.eq(GenerationRecord::getUserId, userId);
        GenerationRecord record = generationRecordMapper.selectOne(recordWrapper);
        if (record == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "生成记录不存在");
        }
        if (record.getStatus() == null || !"success".equalsIgnoreCase(record.getStatus())) {
            if ("failed".equalsIgnoreCase(record.getStatus()) && record.getFailureReason() != null && !record.getFailureReason().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "生成失败：" + record.getFailureReason().trim());
            }
            throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "生成尚未完成，请等待生成成功后再保存");
        }
        if (record.getFileType() == null || !"video".equalsIgnoreCase(record.getFileType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该记录不是视频生成结果，无法保存为角色视频");
        }
        if (record.getContentUrl() == null || record.getContentUrl().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "生成记录内容缺失，请重新生成后再保存");
        }

        // 3. 查找API平台和接口
        // 尝试通过 type = 'upload_character' 查找平台
        ApiPlatform platform = null;
        List<ApiPlatform> platforms = apiPlatformService.getPlatformsByTypeWithDecryptedKey("upload_character", record.getSiteId());
        if (platforms != null && !platforms.isEmpty()) {
            platform = platforms.get(0);
        } else {
             // 降级策略：尝试查找所有平台并匹配名称或描述
             // 暂时只支持通过名称查找
        }

        if (platform == null) {
            // 尝试查找名称中包含 upload-character 的平台
            // 注意：这里需要手动解密apiKey
            LambdaQueryWrapper<ApiPlatform> platformWrapper = new LambdaQueryWrapper<>();
            platformWrapper.like(ApiPlatform::getName, "upload-character")
                           .eq(ApiPlatform::getIsEnabled, true)
                           .eq(ApiPlatform::getSiteId, record.getSiteId());
            platform = apiPlatformService.getOne(platformWrapper);
            
            if (platform != null && platform.getApiKey() != null) {
                 // 解密
                 try {
                     String decrypted = com.meitou.admin.util.AesEncryptUtil.decrypt(platform.getApiKey());
                     platform.setApiKey(decrypted);
                 } catch (Exception e) {
                     log.warn("解密API Key失败", e);
                 }
            }
        }

        if (platform == null) {
            throw new BusinessException(ErrorCode.GENERATION_PLATFORM_NOT_CONFIGURED.getCode(), "未找到配置为 'upload_character' 类型或名称包含 'upload-character' 的API平台");
        }

        // 获取接口配置
        List<ApiInterface> interfaces = apiPlatformService.getInterfacesByPlatformId(platform.getId());
        if (interfaces == null || interfaces.isEmpty()) {
            throw new BusinessException(ErrorCode.GENERATION_INTERFACE_NOT_CONFIGURED.getCode(), "API平台未配置接口");
        }
        ApiInterface apiInterface = interfaces.get(0); // 假设只有一个接口

        // 4. 调用API
        String characterId = null;
        String modelDataJson = null;
        try {
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("pid", request.getPid().trim());
            requestBody.put("timestamps", request.getTimestamps().trim());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 处理Headers配置
            if (apiInterface.getHeaders() != null && !apiInterface.getHeaders().isEmpty()) {
                try {
                    Map<String, String> headerMap = objectMapper.readValue(apiInterface.getHeaders(), Map.class);
                    for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                        String k = entry.getKey();
                        String v = entry.getValue();
                        if (v != null && v.contains("apikey") && platform.getApiKey() != null) {
                            headers.set(k, v.replace("apikey", platform.getApiKey()));
                        } else {
                            headers.set(k, v);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析headers失败", e);
                }
            }
            
            // 添加Authorization
            if (platform.getApiKey() != null && !platform.getApiKey().isEmpty()) {
                // 如果headers里没配Authorization，则自动添加
                if (!headers.containsKey("Authorization")) {
                     headers.set("Authorization", "Bearer " + platform.getApiKey());
                }
            }

            HttpEntity<String> httpEntity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            log.info("调用保存角色API: {}", apiInterface.getUrl());
            ResponseEntity<String> response = restTemplate.exchange(
                    apiInterface.getUrl(),
                    HttpMethod.valueOf(apiInterface.getMethod().toUpperCase()),
                    httpEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.API_CALL_FAILED.getCode(), "API调用失败: " + response.getStatusCode());
            }

            // 5. 解析响应
            // 响应示例: {"id":"xxxxx", "results": [{"character_id": "character.name"}], "status": "succeeded", ...}
            // 或者是 SSE 格式 (多行):
            // data: {"id":"...", "status":"running"...}
            // data: {"id":"...", "status":"succeeded"...}
            String responseBody = response.getBody();
            JsonNode rootNode = null;

            if (responseBody != null) {
                // 按行分割
                String[] lines = responseBody.split("\n");
                // 从最后一行开始往前找，找到第一个包含有效数据的行
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (line.isEmpty()) continue;
                    
                    if (line.startsWith("data:")) {
                        line = line.substring(5).trim();
                    }
                    
                    try {
                        JsonNode node = objectMapper.readTree(line);
                        // 如果解析成功，且包含 status 或 results，则认为是有效节点
                        if (node.has("status") || node.has("results")) {
                            rootNode = node;
                            break;
                        }
                    } catch (Exception e) {
                        // 忽略解析失败的行
                    }
                }
                
                // 如果没找到有效行，尝试解析整个body（非SSE情况）
                if (rootNode == null) {
                    try {
                        rootNode = objectMapper.readTree(responseBody);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
            
            if (rootNode == null) {
                 throw new BusinessException(ErrorCode.PARSE_RESPONSE_FAILED.getCode(), "无法解析API响应");
            }

            String status = rootNode.has("status") ? rootNode.get("status").asText() : null;
            if (status != null && ("failed".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status))) {
                String friendlyError = buildFriendlyThirdPartyError(rootNode);
                log.warn("第三方创建角色失败: pid={}, status={}, error={}", request.getPid(), status, friendlyError);
                throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), friendlyError);
            }

            if (rootNode.has("results") && rootNode.get("results").isArray() && rootNode.get("results").size() > 0) {
                JsonNode firstResult = rootNode.get("results").get(0);
                if (firstResult.has("character_id")) {
                    characterId = firstResult.get("character_id").asText();
                }
            }
            
            if (characterId == null) {
                 // 尝试直接从根节点获取，以防万一
                 if (rootNode.has("character_id")) {
                     characterId = rootNode.get("character_id").asText();
                 }
            }

            if (characterId == null) {
                log.error("API响应中未找到character_id: {}", responseBody);
                if (status != null && ("running".equalsIgnoreCase(status) || "processing".equalsIgnoreCase(status))) {
                    throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), "角色创建尚未完成，请稍后再试");
                }
                String friendlyError = buildFriendlyThirdPartyError(rootNode);
                throw new BusinessException(ErrorCode.API_RESPONSE_ERROR.getCode(), friendlyError);
            }

            // 保存响应数据到model_data
            try {
                modelDataJson = objectMapper.writeValueAsString(rootNode);
            } catch (Exception e) {
                log.warn("序列化响应数据失败", e);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存角色视频失败", e);
            throw new BusinessException(ErrorCode.API_CALL_FAILED.getCode(), "保存角色视频失败，请稍后重试");
        }

        // 6. 保存到数据库
        Character character = new Character();
        character.setUserId(userId);
        character.setSiteId(record.getSiteId()); // 继承记录的站点ID
        character.setModelData(modelDataJson);
        // 名称暂定，或者让前端传？目前需求没提，使用默认或记录的prompt
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            character.setName(request.getName().trim());
        } else {
            character.setName("Character-" + record.getId()); 
            if (record.getPrompt() != null && !record.getPrompt().isEmpty()) {
                 // 截取一部分prompt作为名字
                 String name = record.getPrompt();
                 if (name.length() > 20) name = name.substring(0, 20) + "...";
                 character.setName(name);
            }
        }
        
        character.setCoverUrl(record.getThumbnailUrl()); // 假设封面是缩略图
        character.setVideoUrl(record.getContentUrl());   // 视频URL
        character.setSourceRecordId(record.getId());
        character.setThirdPartyPid(request.getPid());
        character.setCharacterId(characterId);
        character.setDeleted(0);
        
        characterMapper.insert(character);

        // 返回前处理签名URL
        character.setCoverUrl(fileStorageService.getFileUrl(character.getCoverUrl()));
        character.setVideoUrl(fileStorageService.getFileUrl(character.getVideoUrl()));

        Map<String, Object> result = new HashMap<>();
        result.put("id", character.getId());
        result.put("character_id", characterId);
        return Result.success(result);
    }

    private void validateTimestamps(String timestamps) {
        if (!TIMESTAMPS_PATTERN.matcher(timestamps).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "时间戳格式不正确，请使用逗号分隔的数字，例如：0,3");
        }
        String[] parts = timestamps.split(",");
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "时间戳格式不正确，请使用两个数字，例如：0,3");
        }
        long start = Long.parseLong(parts[0]);
        long end = Long.parseLong(parts[1]);
        if (start < 0 || end < 0 || start >= end) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "时间戳范围不正确，请确保起始时间小于结束时间");
        }
    }

    private String buildFriendlyThirdPartyError(JsonNode rootNode) {
        String error = rootNode.has("error") && !rootNode.get("error").isNull() ? rootNode.get("error").asText() : null;
        if (error == null || error.trim().isEmpty()) {
            error = rootNode.has("failure_reason") && !rootNode.get("failure_reason").isNull() ? rootNode.get("failure_reason").asText() : null;
        }

        if (error != null) {
            String normalized = error.trim();
            if (normalized.toLowerCase().contains("does not meet the criteria for creating a character")) {
                return "该视频不符合创建角色的要求，建议重新生成清晰（不包含真人参考、需保证单一人物、无遮挡、光线充足）的视频后重试";
            }
            if (normalized.length() <= 80) {
                return "创建角色失败：" + normalized;
            }
            return "创建角色失败，请更换视频后重试";
        }

        String status = rootNode.has("status") ? rootNode.get("status").asText() : null;
        if (status != null && ("running".equalsIgnoreCase(status) || "processing".equalsIgnoreCase(status))) {
            return "角色创建尚未完成，请稍后再试";
        }
        return "创建角色失败，请更换视频后重试";
    }

    /**
     * 获取用户角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    public List<Character> getUserCharacters(Long userId) {
        LambdaQueryWrapper<Character> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Character::getUserId, userId);
        wrapper.orderByDesc(Character::getCreatedAt);
        List<Character> characters = characterMapper.selectList(wrapper);
        
        // 处理签名URL
        if (characters != null && !characters.isEmpty()) {
            for (Character c : characters) {
                c.setCoverUrl(fileStorageService.getFileUrl(c.getCoverUrl()));
                c.setVideoUrl(fileStorageService.getFileUrl(c.getVideoUrl()));
            }
        }
        
        return characters;
    }

    /**
     * 删除角色
     *
     * @param id     角色ID
     * @param userId 用户ID
     */
    public void deleteCharacter(Long id, Long userId) {
        Character character = characterMapper.selectById(id);
        if (character == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "角色不存在");
        }
        if (!character.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED.getCode(), "无权删除此角色");
        }
        characterMapper.deleteById(id);
    }
}
