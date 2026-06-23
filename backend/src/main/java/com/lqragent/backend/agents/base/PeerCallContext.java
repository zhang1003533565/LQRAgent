package com.lqragent.backend.agents.base;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

/**
 * Agent 间动态协作上下文，限制调用深度并防止循环调用。
 */
@Data
public class PeerCallContext {
    private static final int MAX_DEPTH = 2;

    private int depth = 0;
    private Set<String> visitedPeers = new HashSet<>();

    public boolean canCall(String peerId) {
        return depth < MAX_DEPTH && !visitedPeers.contains(peerId);
    }

    public PeerCallContext enter(String peerId) {
        PeerCallContext next = new PeerCallContext();
        next.setDepth(depth + 1);
        next.getVisitedPeers().addAll(visitedPeers);
        next.getVisitedPeers().add(peerId);
        return next;
    }
}
