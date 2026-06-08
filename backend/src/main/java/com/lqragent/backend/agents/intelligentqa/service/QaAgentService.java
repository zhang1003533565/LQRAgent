package com.lqragent.backend.agents.intelligentqa.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lqragent.backend.chat.entity.AgentRunLog;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.service.AgentRunLogService;
import com.lqragent.backend.chat.service.ChatSessionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @deprecated 已废弃 — QA 功能已迁移到 {@link com.lqragent.backend.agents.serve.qa.QaAgent}（ReAct 模式）。
 * 保留此类仅作参考，不再被 ChatWebSocketHandler 调用。
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class QaAgentService {

    private final ChatSessionService chatSessionService;
    private final AgentRunLogService agentRunLogService;
    private final AiServerWsProxy aiServerWsProxy;

    /**
     * 处理用户消息：转发至 ai-server，通过 callback 将流式响应推回前端。
     *
     * @param userId      用户ID
     * @param sessionId   聊天会话ID
     * @param userMessage 用户消息内容
     * @param callback    流式回调（onChunk / onDone / onError）
     */
    public void handleMessage(Long userId, String sessionId, String userMessage,
                              AiServerWsProxy.StreamCallback callback) {
        long startTime = System.currentTimeMillis();
        AgentRunLog runLog = agentRunLogService.startRun(sessionId, userId, "intelligent_qa", "chat");

        log.info("[QaAgent] handling message: userId={}, sessionId={}, content={}", userId, sessionId, userMessage);

        final boolean[] errorOccurred = {false};

        aiServerWsProxy.streamChat(sessionId, userMessage, userId, new AiServerWsProxy.StreamCallback() {
            @Override
            public void onChunk(String content) {
                callback.onChunk(content);
            }

            @Override
            public void onDone(String aiServerSessionId) {
                if (errorOccurred[0]) {
                    return; // Don't overwrite the error with "complete"
                }
                int durationMs = (int) (System.currentTimeMillis() - startTime);
                agentRunLogService.completeRun(runLog.getId(), durationMs, null);
                if (aiServerSessionId != null) {
                    chatSessionService.updateAiServerSessionId(Long.parseLong(sessionId), aiServerSessionId);
                }
                callback.onDone(aiServerSessionId);
            }

            @Override
            public void onError(String error) {
                errorOccurred[0] = true;
                int durationMs = (int) (System.currentTimeMillis() - startTime);
                agentRunLogService.failRun(runLog.getId(), error);
                callback.onError(error);
            }

            @Override
            public void onSources(List<Map<String, Object>> sources) {
                callback.onSources(sources);
            }
        });
    }
}
