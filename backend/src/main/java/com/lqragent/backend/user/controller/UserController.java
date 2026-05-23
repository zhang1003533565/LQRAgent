package com.lqragent.backend.user.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.user.dto.UserProfileDto;
import com.lqragent.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户", description = "当前用户信息")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息", description = "返回已登录用户的 ID、用户名、角色等")
    @GetMapping("/me")
    public ApiResponse<UserProfileDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok(userService.getCurrentUser(userDetails.getUsername()));
    }
}
