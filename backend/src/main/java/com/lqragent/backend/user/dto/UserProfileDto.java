package com.lqragent.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "用户信息")
@Data
@Builder
public class UserProfileDto {

    @Schema(description = "用户 ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "角色：student / admin")
    private String role;
}
