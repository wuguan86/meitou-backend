package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.dto.ApiParameterMappingRequest;
import com.meitou.admin.dto.ApiParameterMappingResponse;
import com.meitou.admin.service.admin.ApiParameterMappingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/api/mapping")
public class ApiParameterMappingController {

    private final ApiParameterMappingService apiParameterMappingService;

    public ApiParameterMappingController(ApiParameterMappingService apiParameterMappingService) {
        this.apiParameterMappingService = apiParameterMappingService;
    }

    @GetMapping("/list")
    @SiteScope(required = false)
    public Result<Page<ApiParameterMappingResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long platformId,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) Long siteId) {
        return Result.success(apiParameterMappingService.list(page, size, platformId, modelName));
    }

    @PostMapping("/create")
    @SiteScope
    public Result<Void> create(@RequestBody ApiParameterMappingRequest request) {
        apiParameterMappingService.create(request);
        return Result.success();
    }

    @PostMapping("/update/{id}")
    @SiteScope
    public Result<Void> update(@PathVariable Long id, @RequestBody ApiParameterMappingRequest request) {
        apiParameterMappingService.update(id, request);
        return Result.success();
    }

    @PostMapping("/delete/{id}")
    @SiteScope
    public Result<Void> delete(@PathVariable Long id, @RequestParam Long siteId) {
        apiParameterMappingService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @SiteScope
    public Result<ApiParameterMappingResponse> get(@PathVariable Long id) {
        return Result.success(apiParameterMappingService.get(id));
    }
}
