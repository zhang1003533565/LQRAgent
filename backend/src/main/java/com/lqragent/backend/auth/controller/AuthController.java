package com.lqragent.backend.auth.controller;

import com.lqragent.backend.auth.dto.LoginRequest;
import com.lqragent.backend.auth.dto.LoginResponse;
import com.lqragent.backend.auth.dto.RegisterRequest;
import com.lqragent.backend.auth.service.AuthService;
import com.lqragent.backend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "登录、注册、登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录", description = "账号密码登录，返回 JWT token 和角色信息")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "用户注册", description = "注册新学生账号，注册成功后自动登录并返回 JWT token")
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @Operation(summary = "用户登出", description = "无状态 JWT，客户端丢弃 token 即可")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
