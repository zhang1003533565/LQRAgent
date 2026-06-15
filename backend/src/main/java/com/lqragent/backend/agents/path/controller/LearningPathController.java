package com.lqragent.backend.agents.path.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.user.service.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "学习路径", description = "根据学习目标生成个性化路径")
@RestController
@RequestMapping("/api/learning-path")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "生成学习路径", description = "输入学习目标，返回知识点节点列表（真实图谱数据）")
    @GetMapping
    public ApiResponse<LearningPathDto> getPath(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "学习目标，如「学习 Python 装饰器」或知识点ID kp_decorator") @RequestParam String goal,
            @Parameter(description = "当前知识点 ID（可选）") @RequestParam(required = false) String currentKpId) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(learningPathService.generatePath(userId, goal, currentKpId));
    }

    @Operation(summary = "获取当前学习路径", description = "返回当前用户最近一条 ACTIVE 路径")
    @GetMapping("/current")
    public ApiResponse<LearningPathDto> getCurrentPath(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return learningPathService.getCurrentPath(userId)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.fail(404, "暂无活跃学习路径"));
    }

    @Operation(summary = "更新步骤状态", description = "标记学习路径中某步骤为已完成/进行中等")
    @PutMapping("/steps/{pathId}/{kpId}")
    public ApiResponse<Void> updateStepStatus(
            @PathVariable Long pathId,
            @PathVariable String kpId,
            @RequestParam boolean completed,
            @RequestParam(defaultValue = "COMPLETED") String status) {
        boolean ok = learningPathService.updateStepStatus(pathId, kpId, completed, status);
        return ok ? ApiResponse.ok(null) : ApiResponse.fail(404, "步骤不存在");
    }
}
