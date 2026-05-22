package com.lqragent.backend.learningpath.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.learningpath.dto.LearningPathDto;
import com.lqragent.backend.learningpath.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学习路径接口。
 * GET /api/learning-path?goal={goal} → 根据学习目标生成路径
 */
@RestController
@RequestMapping("/api/learning-path")
@RequiredArgsConstructor
public class LearningPathController {

    private final LearningPathService learningPathService;

    @GetMapping
    public ApiResponse<LearningPathDto> getPath(
            @RequestParam String goal,
            @RequestParam(required = false) String currentKpId) {
        return ApiResponse.ok(learningPathService.generatePath(goal, currentKpId));
    }
}
