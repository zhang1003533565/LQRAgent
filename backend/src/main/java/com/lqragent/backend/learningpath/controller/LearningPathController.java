package com.lqragent.backend.learningpath.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.learningpath.dto.LearningPathDto;
import com.lqragent.backend.learningpath.service.LearningPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "学习路径", description = "根据学习目标生成个性化路径")
@RestController
@RequestMapping("/api/learning-path")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;

    @Operation(summary = "获取学习路径", description = "输入学习目标，返回知识点节点列表（当前为占位数据）")
    @GetMapping
    public ApiResponse<LearningPathDto> getPath(
            @Parameter(description = "学习目标，如「学习 Python 装饰器」") @RequestParam String goal,
            @Parameter(description = "当前知识点 ID（可选）") @RequestParam(required = false) String currentKpId) {
        return ApiResponse.ok(learningPathService.generatePath(goal, currentKpId));
    }
}
