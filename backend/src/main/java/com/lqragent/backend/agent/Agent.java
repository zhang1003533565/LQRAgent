package com.lqragent.backend.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能体统一接口。
 * <p>
 * 每个 Agent = System Prompt（角色定义） + Tools（LLM 可调用的函数）。
 * 核心执行由 {@link AgentEngine} 的 LLM 推理循环完成：
 * <pre>
 *   loop: LLM 看上下文 → 选工具 → 执行 → 结果喂回 → 继续
 * </pre>
 * </p>
 */
public interface Agent {

    /** 智能体唯一标识 */
    String agentId();

    /** 统一处理入口（保留向后兼容，新 Agent 委托给 AgentEngine） */
    AgentResult process(AgentTask task);

    /**
     * Agent 的系统提示词 — 定义 LLM 的角色、行为、输出格式。
     * 由 AgentEngine 在推理循环开始前注入为 system message。
     */
    default String getSystemPrompt(AgentTask task) {
        return "你是 " + agentId() + " 智能体。根据用户的请求，选择适当的工具来完成任务。";
    }

    /**
     * Agent 暴露的工具列表 — LLM 在推理循环中可调用的函数。
     * 每个工具包含：名称、描述、参数 JSON Schema。
     */
    default List<ToolSchema> getTools() {
        return Collections.emptyList();
    }

    /**
     * 注册工具执行器到 ToolRegistry。
     * Agent 在此方法中调用 registry.register(agentId, toolName, executor) 注册每个工具。
     * 在 ToolRegistry.init() 中被调用。
     */
    default void registerTools(ToolRegistry registry) {
        // 默认空实现，子类按需重写
    }
}
