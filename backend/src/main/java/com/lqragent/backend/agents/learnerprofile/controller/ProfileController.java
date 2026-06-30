package com.lqragent.backend.agents.learnerprofile.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.agents.learnerprofile.dto.LearningAchievementDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileDetailDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfilePatchRequest;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileExportDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileTrendPointDto;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.learnerprofile.service.ProfileAnalyticsService;
import com.lqragent.backend.agents.learnerprofile.service.ProfileExportService;
import com.lqragent.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "学生画像", description = "学生画像摘要读写")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final LearnerProfileService profileService;
    private final ProfileAnalyticsService profileAnalyticsService;
    private final ProfileExportService profileExportService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "获取画像摘要", description = "返回当前学生的 6 维度画像")
    @GetMapping("/summary")
    public ApiResponse<ProfileSummaryDto> getSummary(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(profileService.getSummary(userId));
    }

    @Operation(summary = "获取画像详情", description = "6 维度 + 知识点掌握地图")
    @GetMapping("/detail")
    public ApiResponse<ProfileDetailDto> getDetail(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.resolveUserId(userDetails);
        return ApiResponse.ok(profileService.getDetail(userId));
    }

    @Operation(summary = "更新画像", description = "局部更新画像字段，只传需要改的字段")
    @PatchMapping("/summary")
    public ApiResponse<ProfileSummaryDto> patch(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProfilePatchRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(profileService.patch(userId, request));
    }

    @Operation(summary = "学习趋势", description = "按时间范围返回掌握度/正确率等趋势数据")
    @GetMapping("/trends")
    public ApiResponse<java.util.List<ProfileTrendPointDto>> getTrends(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(defaultValue = "mastery") String metric) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(profileAnalyticsService.getTrends(userId, range, metric));
    }

    @Operation(summary = "学习成就", description = "返回连续学习、练习、上传等成就进度")
    @GetMapping("/achievements")
    public ApiResponse<java.util.List<LearningAchievementDto>> getAchievements(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(profileAnalyticsService.getAchievements(userId));
    }

    @Operation(summary = "导出学习画像", description = "生成 Markdown 格式学习画像报告")
    @GetMapping("/export")
    public ApiResponse<ProfileExportDto> export(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "markdown") String format) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(profileExportService.export(userId, format));
    }
}
