package com.lqragent.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "注册请求")
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3～64 个字符")
    @Schema(description = "登录用户名", example = "newstudent")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6～64 个字符")
    @Schema(description = "密码", example = "123456")
    private String password;

    @Schema(description = "显示名称，不传则默认使用用户名", example = "张三")
    private String displayName;
}
