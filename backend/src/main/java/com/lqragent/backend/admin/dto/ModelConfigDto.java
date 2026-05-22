package com.lqragent.backend.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 大模型与嵌入模型 API 配置（管理后台展示）。
 */
@Getter
@Builder
public class ModelConfigDto {

    private final String llmBinding;
    private final String llmModel;
    /** 若已配置则返回掩码，未配置为空 */
    private final String llmApiKeyMasked;
    private final boolean llmApiKeySet;
    private final String llmHost;
    private final String llmApiVersion;

    private final String embeddingBinding;
    private final String embeddingModel;
    private final String embeddingApiKeyMasked;
    private final boolean embeddingApiKeySet;
    private final String embeddingHost;
}
