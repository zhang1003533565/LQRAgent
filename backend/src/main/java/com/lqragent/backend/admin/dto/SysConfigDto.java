package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "系统配置项")
@Getter
@Builder
public class SysConfigDto {

    @Schema(description = "记录 ID")
    private final Long id;

    @Schema(description = "配置键名")
    private final String configKey;

    @Schema(description = "配置值")
    private final String configValue;

    @Schema(description = "备注说明")
    private final String remark;

    @Schema(description = "最后更新时间")
    private final LocalDateTime updatedAt;
}
