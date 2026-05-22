package com.lqragent.backend.auth.service;

import com.lqragent.backend.auth.dto.LoginRequest;
import com.lqragent.backend.auth.dto.LoginResponse;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.security.JwtUtil;
import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录业务逻辑：校验账密 → 生成 JWT → 返回角色和跳转路由。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw BusinessException.unauthorized("账号已禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name().toLowerCase())
                .redirectPath(resolveRedirectPath(user.getRole().name()))
                .build();
    }

    private String resolveRedirectPath(String role) {
        return switch (role) {
            case "STUDENT" -> "/workspace";
            case "ADMIN"   -> "/admin";
            default        -> "/";
        };
    }
}
