package com.lqragent.backend.qaagent.service;

import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.observability.entity.AgentRunLog;
import com.lqragent.backend.observability.service.AgentRunLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * P0 默认答疑通道：用户消息直接转发 ai-server 进行流式对话。
 * P1 之后由 Orchestrator 接管意图路由，本 Service 仅处理闲聊/答疑意图。
 */
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
        AgentRunLog runLog = agentRunLogService.startRun(sessionId, userId, "qa_agent", "chat");

        log.info("[QaAgent] handling message: userId={}, sessionId={}, content={}", userId, sessionId, userMessage);

        aiServerWsProxy.streamChat(sessionId, userMessage, new AiServerWsProxy.StreamCallback() {
            @Override
            public void onChunk(String content) {
                callback.onChunk(content);
            }

            @Override
            public void onDone(String aiServerSessionId) {
                int durationMs = (int) (System.currentTimeMillis() - startTime);
                agentRunLogService.completeRun(runLog.getId(), durationMs, null);
                if (aiServerSessionId != null) {
                    chatSessionService.updateAiServerSessionId(sessionId, aiServerSessionId);
                }
                callback.onDone(aiServerSessionId);
            }

            @Override
            public void onError(String error) {
                int durationMs = (int) (System.currentTimeMillis() - startTime);
                agentRunLogService.failRun(runLog.getId(), error);
                callback.onError(error);
            }
        });
    }
}
