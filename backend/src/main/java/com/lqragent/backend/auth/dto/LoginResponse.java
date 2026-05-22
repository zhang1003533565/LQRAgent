package com.lqragent.backend.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    /** JWT token，前端存入 localStorage / sessionStorage */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色：student / teacher / admin */
    private String role;

    /** 登录后前端应跳转的路由 */
    private String redirectPath;
}
