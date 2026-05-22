package com.lqragent.backend.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {

    private Long id;
    private String username;
    private String displayName;
    private String role;
}
