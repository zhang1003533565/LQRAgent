package com.lqragent.backend.orchestrator.planning;

import java.util.List;
import java.util.Map;

import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.base.LlmResponse;
import com.lqragent.backend.agents.base.StreamSink;

/**
 * 测试用 LlmClient：被调用即失败（用于验证快通道不调 LLM）
 */
final class NoOpLlmClient extends LlmClient {

    NoOpLlmClient() {
        super(null);
    }

    @Override
    public LlmResponse chat(String systemPrompt, List<Map<String, Object>> messages,
                            List<Map<String, Object>> tools) {
        throw new AssertionError("LLM should not be called for fast-path intents");
    }

    @Override
    public LlmResponse chatStream(String systemPrompt, List<Map<String, Object>> messages,
                                  StreamSink sink) {
        throw new AssertionError("LLM stream should not be called for fast-path intents");
    }
}
