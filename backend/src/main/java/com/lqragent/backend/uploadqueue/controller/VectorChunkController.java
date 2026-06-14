package com.lqragent.backend.uploadqueue.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.uploadqueue.entity.VectorChunk;
import com.lqragent.backend.uploadqueue.service.VectorChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/vector-chunks")
@RequiredArgsConstructor
@Tag(name = "向量块管理", description = "文档向量化后的切块管理接口")
public class VectorChunkController {

    private final VectorChunkService vectorChunkService;

    @GetMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID查询向量块列表")
    public ApiResponse<List<VectorChunk>> listByTaskId(
            @Parameter(description = "上传任务ID") @PathVariable Long taskId) {
        List<VectorChunk> chunks = vectorChunkService.findByTaskId(taskId);
        return ApiResponse.ok(chunks);
    }

    @GetMapping("/task/{taskId}/page")
    @Operation(summary = "根据任务ID分页查询向量块")
    public ApiResponse<Page<VectorChunk>> pageByTaskId(
            @Parameter(description = "上传任务ID") @PathVariable Long taskId,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<VectorChunk> chunks = vectorChunkService.findByTaskId(taskId, pageable);
        return ApiResponse.ok(chunks);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询单个向量块")
    public ApiResponse<VectorChunk> getById(
            @Parameter(description = "向量块ID") @PathVariable Long id) {
        return vectorChunkService.findById(id)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "向量块不存在"));
    }

    @GetMapping("/index/{indexName}")
    @Operation(summary = "根据索引名称查询向量块")
    public ApiResponse<List<VectorChunk>> listByIndexName(
            @Parameter(description = "向量索引名称") @PathVariable String indexName) {
        List<VectorChunk> chunks = vectorChunkService.findByIndexName(indexName);
        return ApiResponse.ok(chunks);
    }

    @GetMapping("/kp/{kpId}")
    @Operation(summary = "根据知识点ID查询向量块")
    public ApiResponse<List<VectorChunk>> listByKpId(
            @Parameter(description = "知识点ID") @PathVariable String kpId) {
        List<VectorChunk> chunks = vectorChunkService.findByKpId(kpId);
        return ApiResponse.ok(chunks);
    }

    @GetMapping("/task/{taskId}/count")
    @Operation(summary = "统计任务的向量块数量")
    public ApiResponse<Map<String, Object>> countByTaskId(
            @Parameter(description = "上传任务ID") @PathVariable Long taskId) {
        long count = vectorChunkService.countByTaskId(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("chunkCount", count);
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除单个向量块")
    public ApiResponse<Void> deleteById(
            @Parameter(description = "向量块ID") @PathVariable Long id) {
        boolean deleted = vectorChunkService.deleteById(id);
        if (deleted) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(404, "向量块不存在");
    }

    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "删除任务关联的所有向量块")
    public ApiResponse<Void> deleteByTaskId(
            @Parameter(description = "上传任务ID") @PathVariable Long taskId) {
        vectorChunkService.deleteByTaskId(taskId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/index/{indexName}")
    @Operation(summary = "删除索引关联的所有向量块")
    public ApiResponse<Void> deleteByIndexName(
            @Parameter(description = "向量索引名称") @PathVariable String indexName) {
        vectorChunkService.deleteByIndexName(indexName);
        return ApiResponse.ok();
    }
}