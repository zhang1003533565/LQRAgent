package com.lqragent.backend.resourcefacade.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.resourcefacade.dto.ResourceGenerateRequest;
import com.lqragent.backend.resourcefacade.dto.ResourceGenerateResponse;
import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import com.lqragent.backend.resourcefacade.service.ResourceFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "资源生成", description = "为知识点生成讲义/题目/代码/示意图资源")
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceFacadeService resourceFacadeService;

    @Operation(summary = "生成学习资源", description = "为指定知识点生成指定类型的资源内容")
    @PostMapping("/generate")
    public ApiResponse<ResourceGenerateResponse> generate(@Valid @RequestBody ResourceGenerateRequest request) {
        return ApiResponse.ok(resourceFacadeService.generate(request));
    }

    @Operation(summary = "查询知识点资源", description = "查询某知识点已生成的所有资源")
    @GetMapping("/{kpId}")
    public ApiResponse<List<ResourceItem>> getByKpId(@PathVariable String kpId) {
        return ApiResponse.ok(resourceFacadeService.getByKpId(kpId));
    }
}
