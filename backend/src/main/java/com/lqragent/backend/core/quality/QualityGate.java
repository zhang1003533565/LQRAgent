package com.lqragent.backend.core.quality;

import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 质量闸门（Phase 1）。
 * 所有资源/内容类 AgentResult 必须经过此闸门才能返回给用户。
 * fail → Orchestrator 触发重试。
 */
@Slf4j
@Component
public class QualityGate {

    /** 需要经过闸门的 agent 类型 */
    private static final List<String> GATED_AGENTS = List.of(
            AgentIds.RESOURCE_GENERATION,
            AgentIds.CONTENT_ANALYZER,
            AgentIds.QUALITY_ASSESSMENT,
            AgentIds.LEARNING_PATH,
            AgentIds.MEDIA_GENERATION
    );

    /**
     * 检查结果是否需要过质量闸门。
     */
    public boolean requiresGate(String agentType) {
        return GATED_AGENTS.contains(agentType);
    }

    /**
     * 执行质量检查。
     *
     * @param result agent 处理结果
     * @return 检查结果
     */
    public GateResult inspect(AgentResult result) {
        if (!result.isSuccess()) {
            return GateResult.fail("Agent 处理失败: " + result.getErrorMessage());
        }

        // 检查结果是否有内容
        Map<String, Object> data = result.getData();
        if (data == null || data.isEmpty()) {
            return GateResult.fail("结果为空");
        }

        // AgentEngine 返回格式：{"route":"xxx","response":"..."}，跳过字段级检查
        if (data.containsKey("route") || data.containsKey("response")) {
            return GateResult.pass();
        }

        // 检查必需字段（仅对直接 Service 返回的结果）
        String agentType = result.getAgentType();
        if (AgentIds.RESOURCE_GENERATION.equals(agentType)) {
            if (!data.containsKey("content") && !data.containsKey("resourceType")) {
                return GateResult.fail("资源缺少 content/resourceType");
            }
        }
        if (AgentIds.LEARNING_PATH.equals(agentType)) {
            if (!data.containsKey("nodes") || data.get("nodes") == null) {
                return GateResult.fail("路径缺少节点列表");
            }
        }

        return GateResult.pass();
    }

    /** 质检结果 */
    public record GateResult(boolean passed, String reason) {
        public static GateResult pass() { return new GateResult(true, null); }
        public static GateResult fail(String reason) { return new GateResult(false, reason); }
    }
}
