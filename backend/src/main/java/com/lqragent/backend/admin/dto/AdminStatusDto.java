package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "管理后台系统状态")
@Getter
@Builder
public class AdminStatusDto {

    @Schema(description = "后端服务端口")
    private final String serverPort;

    @Schema(description = "AI 服务基地址")
    private final String aiServerBaseUrl;

    @Schema(description = "AI 服务 WebSocket 地址")
    private final String aiServerWsUrl;

    @Schema(description = "是否自动启动 AI 服务")
    private final boolean aiServerAutoStart;

    @Schema(description = "AI 服务是否可达")
    private final boolean aiServerReachable;

    @Schema(description = "系统用户总数")
    private final long userCount;

    @Schema(description = "上传队列任务数")
    private final long uploadTaskCount;
}
