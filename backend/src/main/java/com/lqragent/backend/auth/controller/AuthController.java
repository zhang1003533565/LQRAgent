package com.lqragent.backend.auth.controller;

import com.lqragent.backend.auth.dto.LoginRequest;
import com.lqragent.backend.auth.dto.LoginResponse;
import com.lqragent.backend.auth.service.AuthService;
import com.lqragent.backend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 登录 / 登出接口。
 * POST /api/auth/login  → 返回 JWT + 角色信息
 * POST /api/auth/logout → 客户端丢弃 token 即可
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 无状态 JWT，客户端丢弃 token 即可
        return ApiResponse.ok();
    }
}
