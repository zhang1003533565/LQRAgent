package com.lqragent.backend.agents.base;

/**
 * LLM 流式输出增量回调（Phase 1 → WebSocket chunk）
 */
@FunctionalInterface
public interface StreamSink {

    void onChunk(String delta);
}
