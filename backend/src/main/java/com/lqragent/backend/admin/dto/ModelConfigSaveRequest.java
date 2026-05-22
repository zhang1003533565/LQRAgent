package com.lqragent.backend.admin.dto;

import lombok.Data;

@Data
public class ModelConfigSaveRequest {

    private String llmBinding;
    private String llmModel;
    /** 留空表示不修改已保存的 Key */
    private String llmApiKey;
    private String llmHost;
    private String llmApiVersion;

    private String embeddingBinding;
    private String embeddingModel;
    private String embeddingApiKey;
    private String embeddingHost;

    /** 为 true 时同步写入 ai-server/.env */
    private boolean syncToAiServer = true;
}
