package com.lqragent.backend.user.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.user.dto.UserProfileDto;
import com.lqragent.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息接口。
 * GET /api/users/me → 获取当前登录用户信息
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserProfileDto> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.ok(userService.getCurrentUser(userDetails.getUsername()));
    }
}
