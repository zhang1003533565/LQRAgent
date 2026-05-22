package com.lqragent.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysConfigSaveRequest {

    @NotBlank(message = "配置值不能为空")
    private String configValue;

    private String remark;
}
