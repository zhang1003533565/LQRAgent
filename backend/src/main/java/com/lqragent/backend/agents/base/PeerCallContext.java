package com.lqragent.backend.agents.base;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * Agent 间 peer 调用上下文，用于记录调用链路与来源 Agent。
 */
@Getter
public class PeerCallContext {

    private final List<String> callChain = new ArrayList<>();

    public PeerCallContext enter(String agentId) {
        callChain.add(agentId);
        return this;
    }

    public String currentAgent() {
        return callChain.isEmpty() ? null : callChain.get(callChain.size() - 1);
    }
}
