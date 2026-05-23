package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "管理后台用户列表项")
@Getter
@Builder
public class AdminUserDto {

    @Schema(description = "用户 ID")
    private final Long id;

    @Schema(description = "用户名")
    private final String username;

    @Schema(description = "显示名称")
    private final String displayName;

    @Schema(description = "角色：student / admin")
    private final String role;

    @Schema(description = "是否启用")
    private final Boolean enabled;
}
