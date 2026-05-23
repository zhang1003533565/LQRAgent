package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "大模型配置保存请求")
@Data
public class ModelConfigSaveRequest {

    @Schema(description = "大模型提供商")
    private String llmBinding;

    @Schema(description = "大模型名称")
    private String llmModel;

    @Schema(description = "API Key（留空表示不修改已保存的 Key）")
    private String llmApiKey;

    @Schema(description = "API 地址")
    private String llmHost;

    @Schema(description = "API 版本（Azure 等需要）")
    private String llmApiVersion;

    @Schema(description = "嵌入模型提供商")
    private String embeddingBinding;

    @Schema(description = "嵌入模型名称")
    private String embeddingModel;

    @Schema(description = "嵌入 API Key（留空表示不修改）")
    private String embeddingApiKey;

    @Schema(description = "嵌入 API 地址")
    private String embeddingHost;

    @Schema(description = "是否同步写入 ai-server/.env 文件")
    private boolean syncToAiServer = true;
}
