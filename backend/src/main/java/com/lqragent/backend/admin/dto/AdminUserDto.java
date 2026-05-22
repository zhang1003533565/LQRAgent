package com.lqragent.backend.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserDto {
    private final Long id;
    private final String username;
    private final String displayName;
    private final String role;
    private final Boolean enabled;
}
