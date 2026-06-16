package com.lqragent.backend.orchestrator.pipeline.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.orchestrator.pipeline.dto.PipelineTaskDto;
import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineTaskService;
import com.lqragent.backend.user.service.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Pipeline 任务", description = "查询 Pipeline 执行任务的状态和进度")
@RestController
@RequestMapping("/api/pipeline/tasks")
@RequiredArgsConstructor
public class PipelineTaskController {

    private final PipelineTaskService pipelineTaskService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "查询任务状态", description = "根据 taskId 查询 Pipeline 任务的执行状态、当前步骤、已完成步骤等")
    @GetMapping("/{taskId}")
    public ApiResponse<PipelineTaskDto> getTaskStatus(
            @PathVariable String taskId) {
        return pipelineTaskService.findByTaskId(taskId)
                .map(pipelineTaskService::toDto)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "任务不存在"));
    }

    @Operation(summary = "查询最近任务", description = "返回当前用户最近一次 Pipeline 任务的执行状态")
    @GetMapping("/latest")
    public ApiResponse<PipelineTaskDto> getLatestTask(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return pipelineTaskService.findLatestByUserId(userId)
                .map(pipelineTaskService::toDto)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "暂无任务记录"));
    }

    @Operation(summary = "查询所有任务", description = "返回当前用户所有 Pipeline 任务的执行历史")
    @GetMapping
    public ApiResponse<List<PipelineTaskDto>> listTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        List<PipelineTaskDto> tasks = pipelineTaskService.findByUserId(userId)
                .stream()
                .map(pipelineTaskService::toDto)
                .toList();
        return ApiResponse.ok(tasks);
    }
}
