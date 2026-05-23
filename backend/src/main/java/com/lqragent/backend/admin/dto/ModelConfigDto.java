package com.lqragent.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "大模型与嵌入模型 API 配置")
@Getter
@Builder
public class ModelConfigDto {

    @Schema(description = "大模型提供商，如 openai / spark")
    private final String llmBinding;

    @Schema(description = "大模型名称，如 gpt-4o-mini")
    private final String llmModel;

    @Schema(description = "API Key 掩码，已配置时显示 ********")
    private final String llmApiKeyMasked;

    @Schema(description = "是否已配置 API Key")
    private final boolean llmApiKeySet;

    @Schema(description = "大模型 API 地址")
    private final String llmHost;

    @Schema(description = "大模型 API 版本（Azure 等需要）")
    private final String llmApiVersion;

    @Schema(description = "嵌入模型提供商")
    private final String embeddingBinding;

    @Schema(description = "嵌入模型名称")
    private final String embeddingModel;

    @Schema(description = "嵌入 API Key 掩码")
    private final String embeddingApiKeyMasked;

    @Schema(description = "是否已配置嵌入 API Key")
    private final boolean embeddingApiKeySet;

    @Schema(description = "嵌入 API 地址")
    private final String embeddingHost;
}
