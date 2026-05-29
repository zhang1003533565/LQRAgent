package com.lqragent.backend.chat.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 后端到 ai-server 的 WebSocket 代理。
 * 将 ai-server 的流式输出转换为统一的 chunk/done/error 回调。
 */
@Slf4j
@Component
public class AiServerWsProxy {

    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;

    public AiServerWsProxy(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 流式对话：连接 ai-server WebSocket（/api/v1/ws 统一协议），
     * 发送用户消息，将流式响应通过 callback 传回。
     * <p>
     * ai-server unified WS 协议：
     * - 发送: {"type":"message","content":"...","session_id":"..."}
     * - 接收: content(流式块) / done(完成) / error(失败) / session(会话信息)
     * </p>
     */
    public void streamChat(String sessionId, String userMessage, StreamCallback callback) {
        String wsUrl = runtimeConfig.getAiServerWsUrl();
        log.info("[AiServerWsProxy] connecting to ai-server WS: {}", wsUrl);

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            String[] aiServerSessionId = {null};

            WebSocket ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("[AiServerWsProxy] connected to ai-server");
                            // ai-server unified WS expects: type=message, content, session_id, knowledge_bases, tools
                            String kbName = runtimeConfig.get(ConfigKeys.RAG_KB_NAME, "lqragent-uploads");
                            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("content", userMessage)
                                    .put("session_id", sessionId != null ? sessionId : "");
                            payloadNode.putArray("knowledge_bases").add(kbName);
                            payloadNode.putArray("tools").add("rag");
                            String payload = payloadNode.toString();
                            webSocket.sendText(payload, true);
                            log.debug("[AiServerWsProxy] sent: {}", payload);
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String text = data.toString();
                            log.debug("[AiServerWsProxy] received: {}",
                                    text.length() > 200 ? text.substring(0, 200) + "..." : text);

                            try {
                                JsonNode node = objectMapper.readTree(text);
                                if (!node.has("type")) {
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }

                                String type = node.get("type").asText();
                                switch (type) {
                                    case "content" -> {
                                        // Streaming response chunk (main assistant text)
                                        String content = node.has("content") ? node.get("content").asText() : "";
                                        fullResponse.append(content);
                                        callback.onChunk(content);
                                    }
                                    case "thinking" -> {
                                        // LLM reasoning — forward as chunk for transparency
                                        String thinking = node.has("content") ? node.get("content").asText() : "";
                                        if (!thinking.isBlank()) {
                                            fullResponse.append(thinking);
                                            callback.onChunk(thinking);
                                        }
                                    }
                                    case "result" -> {
                                        // Final result (alternative to done, may contain full text)
                                        String result = node.has("content") ? node.get("content").asText() : "";
                                        if (!result.isBlank() && fullResponse.isEmpty()) {
                                            callback.onChunk(result);
                                        }
                                        callback.onDone(aiServerSessionId[0]);
                                        doneLatch.countDown();
                                    }
                                    case "done" -> {
                                        callback.onDone(aiServerSessionId[0]);
                                        doneLatch.countDown();
                                    }
                                    case "error" -> {
                                        String errorMsg = node.has("content") ? node.get("content").asText()
                                                : node.has("message") ? node.get("message").asText()
                                                : "AI server error";
                                        callback.onError(errorMsg);
                                        doneLatch.countDown();
                                    }
                                    case "session" -> {
                                        if (node.has("session_id")) {
                                            aiServerSessionId[0] = node.get("session_id").asText();
                                        }
                                    }
                                    // stage_start, stage_end, progress, sources, tool_call,
                                    // tool_result, observation — silently skip for P0
                                    default -> {
                                        log.debug("[AiServerWsProxy] skipping event type: {}", type);
                                    }
                                }
                            } catch (Exception e) {
                                // Non-JSON frame — could be raw text
                                fullResponse.append(text);
                                callback.onChunk(text);
                            }

                            if (last && doneLatch.getCount() > 0) {
                                callback.onDone(aiServerSessionId[0]);
                                doneLatch.countDown();
                            }

                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("[AiServerWsProxy] WS error: {}", error.getMessage());
                            callback.onError("AI 服务连接异常: " + error.getMessage());
                            doneLatch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("[AiServerWsProxy] ai-server WS closed: code={}, reason={}", statusCode, reason);
                            if (doneLatch.getCount() > 0) {
                                if (fullResponse.length() > 0) {
                                    callback.onDone(aiServerSessionId[0]);
                                } else {
                                    callback.onError("AI 服务连接已关闭");
                                }
                                doneLatch.countDown();
                            }
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    })
                    .get(10, TimeUnit.SECONDS);

            doneLatch.await(120, TimeUnit.SECONDS);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");

        } catch (Exception e) {
            log.error("[AiServerWsProxy] failed to connect: {}", e.getMessage());
            callback.onError("无法连接 AI 服务: " + e.getMessage());
        }
    }

    /**
     * 流式回调接口。
     */
    public interface StreamCallback {
        void onChunk(String content);
        void onDone(String aiServerSessionId);
        void onError(String error);
    }
}
