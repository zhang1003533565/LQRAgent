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
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AiServerWsProxy {

    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;

    public AiServerWsProxy(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = new ObjectMapper();
    }

    public void streamChat(String sessionId, String userMessage, Long userId, StreamCallback callback) {
        String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        String privatePrefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
        String privateKb = privatePrefix + userId;
        streamChat(sessionId, userMessage, List.of(publicKb, privateKb), callback);
    }

    public void streamChat(String sessionId, String userMessage, List<String> knowledgeBases, StreamCallback callback) {
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
                            var payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("message", userMessage)
                                    .put("content", userMessage)
                                    .put("session_id", sessionId != null ? sessionId : "");
                            String primaryKb = null;
                            var kbArray = payloadNode.putArray("knowledge_bases");
                            for (String knowledgeBase : knowledgeBases) {
                                if (knowledgeBase != null && !knowledgeBase.isBlank()) {
                                    if (primaryKb == null) {
                                        primaryKb = knowledgeBase;
                                    }
                                    kbArray.add(knowledgeBase);
                                }
                            }
                            if (primaryKb != null) {
                                payloadNode.put("enable_rag", true);
                                payloadNode.put("kb_name", primaryKb);
                                payloadNode.putArray("tools").add("rag");
                            }
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
                                    case "stream", "content" -> {
                                        String content = node.has("content") ? node.get("content").asText() : "";
                                        if (!content.isEmpty()) {
                                            fullResponse.append(content);
                                            callback.onChunk(content);
                                        }
                                    }
                                    case "thinking" -> {
                                        String thinking = node.has("content") ? node.get("content").asText() : "";
                                        if (!thinking.isBlank()) {
                                            fullResponse.append(thinking);
                                            callback.onChunk(thinking);
                                        }
                                    }
                                    case "status" -> {
                                        log.debug("[AiServerWsProxy] status: stage={}, message={}",
                                                node.has("stage") ? node.get("stage").asText() : "",
                                                node.has("message") ? node.get("message").asText() : "");
                                    }
                                    case "sources" -> {
                                        try {
                                            if (node.has("rag") && node.get("rag").isArray()) {
                                                @SuppressWarnings("unchecked")
                                                List<Map<String, Object>> ragSources = objectMapper.convertValue(
                                                        node.get("rag"),
                                                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                                                );
                                                callback.onSources(ragSources);
                                            }
                                        } catch (Exception e) {
                                            log.warn("[AiServerWsProxy] parse sources error: {}", e.getMessage());
                                        }
                                    }
                                    case "result" -> {
                                        String result = node.has("content") ? node.get("content").asText() : "";
                                        if (!result.isBlank() && fullResponse.isEmpty()) {
                                            fullResponse.append(result);
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
                                    default -> log.debug("[AiServerWsProxy] skipping event type: {}", type);
                                }
                            } catch (Exception e) {
                                log.warn("[AiServerWsProxy] non-JSON frame skipped: {}",
                                        text.length() > 200 ? text.substring(0, 200) + "..." : text);
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

    public interface StreamCallback {
        void onChunk(String content);

        void onDone(String aiServerSessionId);

        void onError(String error);

        default void onSources(List<Map<String, Object>> sources) {
            // Optional callback for chat flows that surface RAG citations.
        }
    }
}
