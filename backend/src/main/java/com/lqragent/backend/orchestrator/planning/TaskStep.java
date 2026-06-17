package com.lqragent.backend.orchestrator.planning;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 阶段二新增：任务计划单步
 * <p>
 * 由 LLM 通过 create_plan 工具产出，每个步骤指定一个 Agent 执行
 */
@Data
@Builder
public class TaskStep {
    /** 唯一步骤 ID（如 "s1"） */
    private String stepId;
    /** Agent ID（必须在 AgentCardRegistry 中存在） */
    private String agentId;
    /** 动作名 */
    private String action;
    /** 步骤参数 */
    private Map<String, Object> params;
    /** 依赖的 stepId 列表 */
    @Builder.Default
    private List<String> dependsOn = List.of();
    /** 从上游步骤取结果：{上游 stepId: 输入字段名} */
    @Builder.Default
    private Map<String, String> inputFromSteps = Map.of();
    /** 期望产出 ArtifactKind */
    private String outputKind;
    /** 最大重试次数 */
    @Builder.Default
    private int maxRetries = 2;
    /** 是否可选（失败不阻断） */
    @Builder.Default
    private boolean optional = false;
}
