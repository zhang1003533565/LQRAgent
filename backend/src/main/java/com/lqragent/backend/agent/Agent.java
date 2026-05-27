package com.lqragent.backend.agent;

import java.util.Map;

/**
 * 智能体统一接口。
 * 所有业务智能体必须实现此接口，通过 AgentBus 星型拓扑调用。
 * 禁止智能体之间直接交叉引用。
 */
public interface Agent {

    /** 智能体唯一标识 */
    String agentId();

    /** 统一处理入口 */
    AgentResult process(AgentTask task);
}
