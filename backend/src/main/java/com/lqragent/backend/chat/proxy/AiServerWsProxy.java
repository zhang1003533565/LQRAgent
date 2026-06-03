package com.lqragent.backend.chat.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
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
     * 同时检索公共知识库和用户私有知识库。
     */
    public void streamChat(String sessionId, String userMessage, Long userId, StreamCallback callback) {
        String wsUrl = runtimeConfig.getAiServerWsUrl();
        log.info("[AiServerWsProxy] connecting to ai-server WS: {}", wsUrl);

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            String[] aiServerSessionId = {null};
            // Buffer for WebSocket text-frame fragmentation (Java HttpClient may split large messages)
            StringBuilder messageBuffer = new StringBuilder();

            WebSocket ws = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("[AiServerWsProxy] connected to ai-server");
                            // 同时检索公共库和用户私有库
                            String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
                            String privatePrefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
                            String privateKb = privatePrefix + userId;
                            com.fasterxml.jackson.databind.node.ObjectNode payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("content", userMessage)
                                    .put("session_id", sessionId != null ? sessionId : "");
                            com.fasterxml.jackson.databind.node.ArrayNode kbArray = payloadNode.putArray("knowledge_bases");
                            kbArray.add(publicKb);
                            kbArray.add(privateKb);
                            payloadNode.putArray("tools").add("rag");
                            String payload = payloadNode.toString();
                            webSocket.sendText(payload, true);
                            log.debug("[AiServerWsProxy] sent: {}", payload);
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            // Handle WebSocket frame fragmentation: accumulate until last=true
                            messageBuffer.append(data);
                            if (!last) {
                                return WebSocket.Listener.super.onText(webSocket, data, last);
                            }
                            String text = messageBuffer.toString();
                            messageBuffer.setLength(0);

                            log.debug("[AiServerWsProxy] received: {}",
                                    text.length() > 200 ? text.substring(0, 200) + "..." : text);

                            try {
                                JsonNode node = objectMapper.readTree(text);
                                if (!node.has("type")) {
                                    // JSON without type field — skip silently
                                    log.debug("[AiServerWsProxy] skipping event without type field");
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }

                                String type = node.get("type").asText();
                                switch (type) {
                                    case "content" -> {
                                        // Streaming response chunk — the actual assistant text
                                        String content = node.has("content") ? node.get("content").asText() : "";
                                        if (!content.isEmpty()) {
                                            fullResponse.append(content);
                                            callback.onChunk(content);
                                        }
                                    }
                                    case "thinking" -> {
                                        // LLM internal reasoning — do NOT forward to user
                                        // (thinking is internal chain-of-thought, not part of the response)
                                        log.debug("[AiServerWsProxy] thinking event suppressed ({} chars)",
                                                node.has("content") ? node.get("content").asText().length() : 0);
                                    }
                                    case "result" -> {
                                        // Final result — ai-server puts full text in metadata.response
                                        // Only use as fallback if no content chunks were received
                                        String result = node.has("content") ? node.get("content").asText() : "";
                                        if (result.isBlank() && node.has("metadata")) {
                                            JsonNode meta = node.get("metadata");
                                            if (meta.has("response")) {
                                                result = meta.get("response").asText();
                                            }
                                        }
                                        if (!result.isBlank() && fullResponse.isEmpty()) {
                                            callback.onChunk(result);
                                            fullResponse.append(result);
                                            log.info("[AiServerWsProxy] using result.metadata.response as fallback ({} chars)",
                                                    result.length());
                                        }
                                    }
                                    case "done" -> {
                                        // Check metadata.status — failed turns send error+done pair
                                        String status = "completed";
                                        if (node.has("metadata") && node.get("metadata").has("status")) {
                                            status = node.get("metadata").get("status").asText();
                                        }
                                        if ("failed".equals(status) || "cancelled".equals(status)) {
                                            log.warn("[AiServerWsProxy] turn ended with status={}", status);
                                            // error event already handled; skip duplicate onDone
                                        } else {
                                            callback.onDone(aiServerSessionId[0]);
                                        }
                                        doneLatch.countDown();
                                    }
                                    case "error" -> {
                                        String errorMsg = node.has("content") ? node.get("content").asText()
                                                : node.has("message") ? node.get("message").asText()
                                                : "AI server error";
                                        log.warn("[AiServerWsProxy] error event: {}", errorMsg);
                                        callback.onError(errorMsg);
                                        doneLatch.countDown();
                                    }
                                    case "session" -> {
                                        if (node.has("session_id")) {
                                            aiServerSessionId[0] = node.get("session_id").asText();
                                        }
                                    }
                                    case "sources" -> {
                                        try {
                                            // ai-server puts sources in metadata.sources (StreamEvent protocol)
                                            JsonNode sourcesNode = null;
                                            if (node.has("metadata") && node.get("metadata").has("sources")) {
                                                sourcesNode = node.get("metadata").get("sources");
                                            } else if (node.has("sources")) {
                                                // Fallback: top-level sources field
                                                sourcesNode = node.get("sources");
                                            }
                                            if (sourcesNode != null && sourcesNode.isArray() && sourcesNode.size() > 0) {
                                                List<Map<String, Object>> sources = objectMapper
                                                        .convertValue(sourcesNode, new TypeReference<>() {});
                                                callback.onSources(sources);
                                                log.info("[AiServerWsProxy] forwarded {} RAG sources", sources.size());
                                            }
                                        } catch (Exception e) {
                                            log.warn("[AiServerWsProxy] parse sources error: {}", e.getMessage());
                                        }
                                    }
                                    default -> {
                                        // stage_start, stage_end, progress, tool_call,
                                        // tool_result, observation — silently skip
                                    }
                                }
                            } catch (Exception e) {
                                // Non-JSON frame — log and skip (do NOT append to response)
                                log.warn("[AiServerWsProxy] non-JSON frame skipped: {}",
                                        text.length() > 100 ? text.substring(0, 100) + "..." : text);
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

            boolean completed = doneLatch.await(120, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[AiServerWsProxy] turn timed out after 120s, response length={}", fullResponse.length());
                if (fullResponse.length() > 0) {
                    callback.onDone(aiServerSessionId[0]);
                } else {
                    callback.onError("AI 服务响应超时，请稍后重试");
                }
            }
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

        /** 接收 RAG 引用来源（知识库检索结果），默认空实现 */
        default void onSources(List<Map<String, Object>> sources) {}
    }
}
