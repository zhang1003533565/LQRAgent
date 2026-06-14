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

    @Schema(description = "视频生成提供商")
    private final String videoBinding;

    @Schema(description = "视频生成模型")
    private final String videoModel;

    @Schema(description = "视频生成 API Key 掩码")
    private final String videoApiKeyMasked;

    @Schema(description = "是否已配置视频生成 API Key")
    private final boolean videoApiKeySet;

    @Schema(description = "视频生成 API 地址")
    private final String videoHost;

    @Schema(description = "图片生成提供商")
    private final String imageBinding;

    @Schema(description = "图片生成模型")
    private final String imageModel;

    @Schema(description = "图片生成 API Key 掩码")
    private final String imageApiKeyMasked;

    @Schema(description = "是否已配置图片生成 API Key")
    private final boolean imageApiKeySet;

    @Schema(description = "图片生成 API 地址")
    private final String imageHost;

    @Schema(description = "视觉识别/OCR 提供商")
    private final String ocrBinding;

    @Schema(description = "视觉识别/OCR 模型")
    private final String ocrModel;

    @Schema(description = "OCR API Key 掩码")
    private final String ocrApiKeyMasked;

    @Schema(description = "是否已配置 OCR API Key")
    private final boolean ocrApiKeySet;

    @Schema(description = "OCR Secret Key 掩码（讯飞等需要）")
    private final String ocrSecretKeyMasked;

    @Schema(description = "是否已配置 OCR Secret Key")
    private final boolean ocrSecretKeySet;

    @Schema(description = "OCR API 地址")
    private final String ocrHost;
}
