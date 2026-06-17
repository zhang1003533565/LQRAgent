package com.lqragent.backend.agents.base;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lqragent.backend.orchestrator.artifact.Artifact;
import lombok.Builder;
import lombok.Data;

/**
 * Agent 统一响应格式
 * <p>
 * 同时支持：
 * 1. PipelineEngine / BaseAgent 使用的 record 风格（success/content/error/metadata）
 * 2. Agent 工具使用的 DTO 风格（type/title/summary/content/data）
 * </p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    // ===== Pipeline 风格字段 =====
    private boolean success;
    private String content;
    private String error;
    private List<ToolExecution> executions;
    private Map<String, Object> metadata;

    // ===== DTO 风格字段（已废弃，阶段八将删除） =====
    @Deprecated private String type;
    @Deprecated private String title;
    @Deprecated private String summary;
    @Deprecated private Object data;

    // ===== 阶段一新增：统一 Artifact 列表 =====
    /** 统一产物列表（替代散落在 metadata 里的 artifactKind/artifactPayload） */
    private List<Artifact> artifacts;

    /**
     * 创建成功响应（Pipeline 风格）
     */
    public static AgentResponse success(String content, List<ToolExecution> executions, Map<String, Object> metadata) {
        return AgentResponse.builder()
                .success(true)
                .content(content)
                .executions(executions)
                .metadata(metadata)
                .build();
    }

    /**
     * 创建失败响应（Pipeline 风格）
     */
    public static AgentResponse failure(String error) {
        return AgentResponse.builder()
                .success(false)
                .error(error)
                .build();
    }

    /**
     * 创建成功响应（DTO 风格，Agent 工具使用）
     */
    public static AgentResponse success(String type, String title, String content) {
        return AgentResponse.builder()
                .success(true)
                .type(type)
                .title(title)
                .summary(title)
                .content(content)
                .build();
    }

    /**
     * 创建带数据的响应（DTO 风格）
     */
    public static AgentResponse withData(String type, String title, String summary, String content, Object data) {
        return AgentResponse.builder()
                .success(true)
                .type(type)
                .title(title)
                .summary(summary)
                .content(content)
                .data(data)
                .build();
    }

    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"success\":" + success + ",\"content\":\"" + content + "\"}";
        }
    }

    // ===== 阶段一新增：带 Artifact 的成功响应 =====

    /**
     * 创建带 Artifact 列表的成功响应
     */
    public static AgentResponse successWithArtifacts(String content, List<Artifact> artifacts,
                                                     Map<String, Object> metadata) {
        return AgentResponse.builder()
                .success(true)
                .content(content)
                .artifacts(artifacts)
                .metadata(metadata)
                .build();
    }

    /**
     * 创建带单个 Artifact 的成功响应
     */
    public static AgentResponse successWithArtifact(String content, Artifact artifact) {
        return successWithArtifacts(content, artifact != null ? List.of(artifact) : List.of(), Map.of());
    }
}
