package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "系统配置保存请求")
@Data
public class SysConfigSaveRequest {

    @NotBlank(message = "配置值不能为空")
    @Schema(description = "配置值")
    private String configValue;

    @Schema(description = "备注说明（可选）")
    private String remark;
}
