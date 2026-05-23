package com.lqragent.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "登录/注册响应，注册成功也会自动返回 JWT token")
@Data
@Builder
public class LoginResponse {

    @Schema(description = "JWT token，后续请求需携带在 Authorization 头中")
    private String token;

    @Schema(description = "用户 ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "角色：student / admin")
    private String role;

    @Schema(description = "登录后建议跳转的路由，如 /workspace 或 /admin")
    private String redirectPath;
}
