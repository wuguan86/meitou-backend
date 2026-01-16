package com.meitou.admin.controller.admin;

import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.ApiInterfaceRequest;
import com.meitou.admin.dto.ApiInterfaceResponse;
import com.meitou.admin.dto.ApiPlatformRequest;
import com.meitou.admin.dto.ApiPlatformResponse;
import com.meitou.admin.entity.ApiInterface;
import com.meitou.admin.entity.ApiPlatform;
import com.meitou.admin.service.admin.ApiPlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端API平台管理控制器
 */
@RestController
@RequestMapping("/api/admin/api-platforms")
@RequiredArgsConstructor
public class ApiPlatformController {
    
    private final ApiPlatformService platformService;
    
    /**
     * 获取平台列表（包含接口）
     * 
     * @param siteId 站点ID
     * @return 平台列表
     */
    @GetMapping
    @SiteScope
    public Result<List<ApiPlatformResponse>> getPlatforms(@RequestParam("siteId") Long siteId) {
        if (siteId == null) {
            return Result.error("站点ID不能为空");
        }
        
        List<ApiPlatform> platforms = platformService.getPlatforms(siteId);
        
        // 转换为响应DTO，包含接口列表
        List<ApiPlatformResponse> responses = platforms.stream().map(platform -> {
            ApiPlatformResponse response = new ApiPlatformResponse();
            response.setId(platform.getId());
            response.setName(platform.getName());
            response.setAlias(platform.getAlias());
            response.setSiteId(platform.getSiteId());
            response.setNodeInfo(platform.getNodeInfo());
            response.setIsEnabled(platform.getIsEnabled());
            // 如果apiKey已设置，返回占位符，避免前端显示加密后的字符串
            response.setApiKey(platform.getApiKey() != null && !platform.getApiKey().isEmpty() ? "***已设置***" : null);
            response.setDescription(platform.getDescription());
            response.setSupportedModels(platform.getSupportedModels());
            response.setType(platform.getType());
            
            // 获取接口列表
            List<ApiInterface> interfaces = platformService.getInterfacesByPlatformId(platform.getId());
            List<ApiInterfaceResponse> interfaceResponses = interfaces.stream().map(iface -> {
                ApiInterfaceResponse ifaceResp = new ApiInterfaceResponse();
                ifaceResp.setId(iface.getId());
                ifaceResp.setUrl(iface.getUrl());
                ifaceResp.setMethod(iface.getMethod());
                ifaceResp.setResponseMode(iface.getResponseMode());
                ifaceResp.setHeaders(iface.getHeaders());
                ifaceResp.setParametersJson(iface.getParametersJson());
                ifaceResp.setParamDocs(iface.getParamDocs());
                return ifaceResp;
            }).collect(Collectors.toList());
            
            response.setInterfaces(interfaceResponses);
            return response;
        }).collect(Collectors.toList());
        
        return Result.success(responses);
    }
    
    /**
     * 获取平台详情
     * 
     * @param id 平台ID
     * @param siteId 站点ID
     * @return 平台详情
     */
    @GetMapping("/{id}")
    @SiteScope
    public Result<ApiPlatformResponse> getPlatform(@PathVariable Long id, @RequestParam("siteId") Long siteId) {
        if (siteId == null) {
            return Result.error("站点ID不能为空");
        }
        ApiPlatform platform = platformService.getPlatformById(id);
        
        // 转换为响应DTO
        ApiPlatformResponse response = new ApiPlatformResponse();
        response.setId(platform.getId());
        response.setName(platform.getName());
        response.setAlias(platform.getAlias());
        response.setSiteId(platform.getSiteId());
        response.setNodeInfo(platform.getNodeInfo());
        response.setIsEnabled(platform.getIsEnabled());
        // apiKey返回占位符，避免显示加密后的字符串
        response.setApiKey(platform.getApiKey() != null && !platform.getApiKey().isEmpty() ? "***已设置***" : null);
        response.setDescription(platform.getDescription());
        response.setSupportedModels(platform.getSupportedModels());
        response.setType(platform.getType());
        
        // 获取接口列表
        List<ApiInterface> interfaces = platformService.getInterfacesByPlatformId(platform.getId());
        List<ApiInterfaceResponse> interfaceResponses = interfaces.stream().map(iface -> {
            ApiInterfaceResponse ifaceResp = new ApiInterfaceResponse();
            ifaceResp.setId(iface.getId());
            ifaceResp.setUrl(iface.getUrl());
            ifaceResp.setMethod(iface.getMethod());
            ifaceResp.setResponseMode(iface.getResponseMode());
            ifaceResp.setHeaders(iface.getHeaders());
            ifaceResp.setParametersJson(iface.getParametersJson());
            ifaceResp.setParamDocs(iface.getParamDocs());
            return ifaceResp;
        }).collect(Collectors.toList());
        
        response.setInterfaces(interfaceResponses);
        return Result.success(response);
    }
    
    /**
     * 创建平台
     * 
     * @param request 平台请求
     * @return 创建的平台
     */
    @PostMapping
    @SiteScope // 使用 AOP 自动处理 SiteContext（从 RequestBody 中获取 siteId）
    public Result<ApiPlatformResponse> createPlatform(@RequestBody ApiPlatformRequest request) {
        // SiteContext 已由 @SiteScope 注解自动设置（从 request.siteId 中获取）
        // 验证必填字段
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return Result.error("平台名称不能为空");
        }
        if (request.getType() == null || request.getType().trim().isEmpty()) {
            return Result.error("接口类型不能为空");
        }
        if (request.getSiteId() == null) {
            return Result.error("站点ID不能为空，请选择所属站点（医美类=1，电商类=2，生活服务类=3）");
        }
        
        // 转换为实体
        ApiPlatform platform = new ApiPlatform();
        platform.setName(request.getName());
        platform.setAlias(request.getAlias());
        platform.setSiteId(request.getSiteId());
        platform.setNodeInfo(request.getNodeInfo());
        platform.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
        platform.setApiKey(request.getApiKey());
        platform.setDescription(request.getDescription());
        platform.setSupportedModels(request.getSupportedModels());
        platform.setType(request.getType());
        
        // 转换接口列表
        List<ApiInterface> interfaces = new ArrayList<>();
        if (request.getInterfaces() != null) {
            for (ApiInterfaceRequest ifaceReq : request.getInterfaces()) {
                ApiInterface apiInterface = new ApiInterface();
                apiInterface.setUrl(ifaceReq.getUrl());
                apiInterface.setMethod(ifaceReq.getMethod());
                apiInterface.setResponseMode(ifaceReq.getResponseMode());
                apiInterface.setHeaders(ifaceReq.getHeaders());
                apiInterface.setParametersJson(ifaceReq.getParametersJson());
                apiInterface.setParamDocs(ifaceReq.getParamDocs());
                interfaces.add(apiInterface);
            }
        }
        
        ApiPlatform created = platformService.createPlatform(platform, interfaces);
        
        // 转换为响应DTO（apiKey返回占位符）
        ApiPlatformResponse response = new ApiPlatformResponse();
        response.setId(created.getId());
        response.setName(created.getName());
        response.setAlias(created.getAlias());
        response.setSiteId(created.getSiteId());
        response.setNodeInfo(created.getNodeInfo());
        response.setIsEnabled(created.getIsEnabled());
        response.setApiKey(created.getApiKey() != null && !created.getApiKey().isEmpty() ? "***已设置***" : null);
        response.setDescription(created.getDescription());
        response.setSupportedModels(created.getSupportedModels());
        response.setType(created.getType());
        
        // 获取接口列表
        List<ApiInterface> createdInterfaces = platformService.getInterfacesByPlatformId(created.getId());
        List<ApiInterfaceResponse> interfaceResponses = createdInterfaces.stream().map(iface -> {
            ApiInterfaceResponse ifaceResp = new ApiInterfaceResponse();
            ifaceResp.setId(iface.getId());
            ifaceResp.setUrl(iface.getUrl());
            ifaceResp.setMethod(iface.getMethod());
            ifaceResp.setResponseMode(iface.getResponseMode());
            ifaceResp.setHeaders(iface.getHeaders());
            ifaceResp.setParametersJson(iface.getParametersJson());
            ifaceResp.setParamDocs(iface.getParamDocs());
            return ifaceResp;
        }).collect(Collectors.toList());
        
        response.setInterfaces(interfaceResponses);
        return Result.success("创建成功", response);
    }
    
    /**
     * 更新平台
     * 
     * @param id 平台ID
     * @param request 平台请求
     * @param siteId 站点ID（用于验证权限和定位原平台）
     * @return 更新后的平台
     */
    @PutMapping("/{id}")
    @SiteScope
    public Result<ApiPlatformResponse> updatePlatform(@PathVariable Long id, @RequestBody ApiPlatformRequest request, @RequestParam("siteId") Long siteId) {
        // SiteContext 由 @SiteScope 注解根据 siteId 参数设置
        // 验证必填字段
        if (request.getSiteId() == null) {
            return Result.error("站点ID不能为空，请选择所属站点（医美类=1，电商类=2，生活服务类=3）");
        }
        // 转换为实体
        ApiPlatform platform = new ApiPlatform();
        platform.setName(request.getName());
        platform.setAlias(request.getAlias());
        platform.setSiteId(request.getSiteId());
        platform.setNodeInfo(request.getNodeInfo());
        platform.setIsEnabled(request.getIsEnabled());
        // 如果前端传入的是占位符或空字符串，则不更新apiKey
        // 只有当前端传入新的apiKey时才更新
        if (request.getApiKey() != null && !request.getApiKey().isEmpty() && !"***已设置***".equals(request.getApiKey())) {
            platform.setApiKey(request.getApiKey());
        } else {
            platform.setApiKey(null); // 设置为null，Service层会跳过更新
        }
        platform.setDescription(request.getDescription());
        platform.setSupportedModels(request.getSupportedModels());
        platform.setType(request.getType());
        
        // 转换接口列表
        List<ApiInterface> interfaces = new ArrayList<>();
        if (request.getInterfaces() != null) {
            for (ApiInterfaceRequest ifaceReq : request.getInterfaces()) {
                ApiInterface apiInterface = new ApiInterface();
                apiInterface.setUrl(ifaceReq.getUrl());
                apiInterface.setMethod(ifaceReq.getMethod());
                apiInterface.setResponseMode(ifaceReq.getResponseMode());
                apiInterface.setHeaders(ifaceReq.getHeaders());
                apiInterface.setParametersJson(ifaceReq.getParametersJson());
                apiInterface.setParamDocs(ifaceReq.getParamDocs());
                interfaces.add(apiInterface);
            }
        }
        
        ApiPlatform updated = platformService.updatePlatform(id, platform, interfaces);
        
        // 转换为响应DTO（apiKey返回占位符）
        ApiPlatformResponse response = new ApiPlatformResponse();
        response.setId(updated.getId());
        response.setName(updated.getName());
        response.setAlias(updated.getAlias());
        response.setSiteId(updated.getSiteId());
        response.setNodeInfo(updated.getNodeInfo());
        response.setIsEnabled(updated.getIsEnabled());
        response.setApiKey(updated.getApiKey() != null && !updated.getApiKey().isEmpty() ? "***已设置***" : null);
        response.setDescription(updated.getDescription());
        response.setSupportedModels(updated.getSupportedModels());
        response.setType(updated.getType());
        
        // 获取接口列表
        List<ApiInterface> updatedInterfaces = platformService.getInterfacesByPlatformId(updated.getId());
        List<ApiInterfaceResponse> interfaceResponses = updatedInterfaces.stream().map(iface -> {
            ApiInterfaceResponse ifaceResp = new ApiInterfaceResponse();
            ifaceResp.setId(iface.getId());
            ifaceResp.setUrl(iface.getUrl());
            ifaceResp.setMethod(iface.getMethod());
            ifaceResp.setResponseMode(iface.getResponseMode());
            ifaceResp.setHeaders(iface.getHeaders());
            ifaceResp.setParametersJson(iface.getParametersJson());
            ifaceResp.setParamDocs(iface.getParamDocs());
            return ifaceResp;
        }).collect(Collectors.toList());
        
        response.setInterfaces(interfaceResponses);
        return Result.success("更新成功", response);
    }
    
    /**
     * 删除平台
     * 
     * @param id 平台ID
     * @param siteId 站点ID（必传，用于验证）
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @SiteScope // 使用 AOP 自动处理 SiteContext（虽然 api_platforms 表不需要多租户过滤，但保持一致性）
    public Result<Void> deletePlatform(@PathVariable Long id, @RequestParam(value = "siteId", required = true) Long siteId) {
        // SiteContext 已由 @SiteScope 注解自动设置（虽然这里不使用，但保持一致性）
        // 获取平台信息，验证站点ID是否匹配
        ApiPlatform platform = platformService.getPlatformById(id);
        if (platform == null) {
            return Result.error("平台不存在");
        }
        if (!siteId.equals(platform.getSiteId())) {
            return Result.error("站点ID不匹配，无法删除该平台");
        }
        platformService.deletePlatform(id);
        return Result.success("删除成功");
    }
}

