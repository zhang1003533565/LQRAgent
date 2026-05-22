package com.lqragent.backend.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SysConfigDto {
    private final Long id;
    private final String configKey;
    private final String configValue;
    private final String remark;
    private final LocalDateTime updatedAt;
}
