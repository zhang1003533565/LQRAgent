package com.lqragent.backend.chat.proxy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AiServerWsProxy {

    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;
    /** 复用的 HttpClient 单例，避免每次调用都创建新连接 */
    private final HttpClient sharedHttpClient;

    public AiServerWsProxy(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.objectMapper = new ObjectMapper();
        this.sharedHttpClient = HttpClient.newHttpClient();
    }

    public void streamChat(String sessionId, String userMessage, Long userId, StreamCallback callback) {
        String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        String privatePrefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
        String privateKb = privatePrefix + userId;
        streamChat(sessionId, userMessage, List.of(publicKb, privateKb), callback);
    }

    /**
     * 获取旧的 /api/v1/chat 端点 URL（streamChat 专用，支持 RAG 参数）。
     */
    private String getChatWsUrl() {
        String base = runtimeConfig.getAiServerBaseUrl();
        if (base.startsWith("https://")) {
            return "wss://" + base.substring("https://".length()) + "/api/v1/chat";
        }
        if (base.startsWith("http://")) {
            return "ws://" + base.substring("http://".length()) + "/api/v1/chat";
        }
        return "ws://localhost:8001/api/v1/chat";
    }

    public void streamChat(String sessionId, String userMessage, List<String> knowledgeBases, StreamCallback callback) {
        String wsUrl = getChatWsUrl();
        int connectTimeoutSec = runtimeConfig.getWsConnectTimeoutSec();
        int responseTimeoutSec = runtimeConfig.getWsResponseTimeoutSec();
        log.info("[AiServerWsProxy] connecting to ai-server WS: {} (connect={}s, response={}s)", wsUrl, connectTimeoutSec, responseTimeoutSec);

        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            AtomicReference<String> aiServerSessionId = new AtomicReference<>();

            WebSocket ws = sharedHttpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("[AiServerWsProxy] connected to ai-server");
                            // 添加中文回复指令
                            String chineseInstruction = "[System Instruction: Please respond in Chinese (中文). Use Simplified Chinese for all responses.]\\n\\n";
                            var payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("message", chineseInstruction + userMessage)
                                    .put("content", chineseInstruction + userMessage)
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
                                            List<Map<String, Object>> allSources = new java.util.ArrayList<>();
                                            // Format 1: metadata.sources (StreamEvent protocol from unified_ws)
                                            if (node.has("metadata") && node.get("metadata").has("sources")) {
                                                JsonNode sourcesNode = node.get("metadata").get("sources");
                                                if (sourcesNode.isArray()) {
                                                    allSources.addAll(objectMapper.convertValue(sourcesNode, new TypeReference<>() {}));
                                                }
                                            }
                                            // Format 2: top-level "sources" array
                                            if (node.has("sources") && node.get("sources").isArray()) {
                                                allSources.addAll(objectMapper.convertValue(node.get("sources"), new TypeReference<>() {}));
                                            }
                                            // Format 3: chat.py format {"type":"sources","rag":[...],"web":[...]}
                                            if (node.has("rag") && node.get("rag").isArray()) {
                                                for (JsonNode item : node.get("rag")) {
                                                    Map<String, Object> src = objectMapper.convertValue(item, new TypeReference<>() {});
                                                    src.putIfAbsent("type", "rag");
                                                    allSources.add(src);
                                                }
                                            }
                                            if (node.has("web") && node.get("web").isArray()) {
                                                for (JsonNode item : node.get("web")) {
                                                    Map<String, Object> src = objectMapper.convertValue(item, new TypeReference<>() {});
                                                    src.putIfAbsent("type", "web");
                                                    allSources.add(src);
                                                }
                                            }
                                            if (!allSources.isEmpty()) {
                                                callback.onSources(allSources);
                                                log.info("[AiServerWsProxy] forwarded {} sources", allSources.size());
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
                                        callback.onDone(aiServerSessionId.get());
                                        doneLatch.countDown();
                                    }
                                    case "done" -> {
                                        callback.onDone(aiServerSessionId.get());
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
                                            aiServerSessionId.set(node.get("session_id").asText());
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
                                    callback.onDone(aiServerSessionId.get());
                                } else {
                                    callback.onError("AI 服务连接已关闭");
                                }
                                doneLatch.countDown();
                            }
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    })
                    .get(connectTimeoutSec, TimeUnit.SECONDS);

            boolean completed = doneLatch.await(responseTimeoutSec, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[AiServerWsProxy] turn timed out after {}s, response length={}", responseTimeoutSec, fullResponse.length());
                if (fullResponse.length() > 0) {
                    callback.onDone(aiServerSessionId.get());
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
     * 通用 capability 调用（同步等待完整响应）。
     * 通过 /api/v1/ws + capability 参数调用 ai-server 的指定 capability。
     *
     * @param capability capability 名称（如 llm_generate）
     * @param config     capability 配置参数（如 prompt_type, title, description 等）
     * @return LLM 生成的完整文本，失败返回 null
     */
    public String callCapability(String capability, Map<String, Object> config) {
        return callCapability(capability, config, null);
    }

    /**
     * 通用 capability 调用（同步等待完整响应），支持知识库。
     */
    public String callCapability(String capability, Map<String, Object> config, List<String> knowledgeBases) {
        String wsUrl = runtimeConfig.getAiServerWsUrl();
        int connectTimeoutSec = runtimeConfig.getWsConnectTimeoutSec();
        int responseTimeoutSec = runtimeConfig.getWsResponseTimeoutSec();
        log.info("[AiServerWsProxy] callCapability: capability={}, wsUrl={} (connect={}s, response={}s)", capability, wsUrl, connectTimeoutSec, responseTimeoutSec);

        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            AtomicReference<String> errorMsg = new AtomicReference<>();

            WebSocket ws = sharedHttpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            var payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("capability", capability)
                                    .put("content", "")
                                    .put("session_id", "");
                            // Add config
                            if (config != null && !config.isEmpty()) {
                                payloadNode.set("config", objectMapper.valueToTree(config));
                            }
                            // Add knowledge_bases if provided
                            if (knowledgeBases != null && !knowledgeBases.isEmpty()) {
                                var kbArray = payloadNode.putArray("knowledge_bases");
                                for (String kb : knowledgeBases) {
                                    if (kb != null && !kb.isBlank()) {
                                        kbArray.add(kb);
                                    }
                                }
                            }
                            String payload = payloadNode.toString();
                            webSocket.sendText(payload, true);
                            log.debug("[AiServerWsProxy] sent capability request: {}", payload);
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String text = data.toString();
                            try {
                                JsonNode node = objectMapper.readTree(text);
                                if (!node.has("type")) {
                                    return WebSocket.Listener.super.onText(webSocket, data, last);
                                }
                                String type = node.get("type").asText();
                                switch (type) {
                                    case "content", "stream" -> {
                                        String c = node.has("content") ? node.get("content").asText() : "";
                                        if (!c.isEmpty()) {
                                            fullResponse.append(c);
                                        }
                                    }
                                    case "result" -> {
                                        String r = node.has("content") ? node.get("content").asText() : "";
                                        if (!r.isBlank() && fullResponse.isEmpty()) {
                                            fullResponse.append(r);
                                        }
                                        doneLatch.countDown();
                                    }
                                    case "done" -> doneLatch.countDown();
                                    case "error" -> {
                                        errorMsg.set(node.has("content") ? node.get("content").asText() : "Unknown error");
                                        doneLatch.countDown();
                                    }
                                    default -> { /* skip */ }
                                }
                            } catch (Exception e) {
                                log.debug("[AiServerWsProxy] non-JSON frame: {}", text.length() > 100 ? text.substring(0, 100) : text);
                            }
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("[AiServerWsProxy] WS error: {}", error.getMessage());
                            errorMsg.set("WS error: " + error.getMessage());
                            doneLatch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            if (doneLatch.getCount() > 0) {
                                if (fullResponse.length() > 0) {
                                    doneLatch.countDown();
                                } else {
                                    errorMsg.set("Connection closed unexpectedly");
                                    doneLatch.countDown();
                                }
                            }
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    })
                    .get(connectTimeoutSec, TimeUnit.SECONDS);

            boolean completed = doneLatch.await(responseTimeoutSec, TimeUnit.SECONDS);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");

            if (!completed) {
                log.warn("[AiServerWsProxy] capability call timed out: capability={}", capability);
                return null;
            }
            if (errorMsg.get() != null) {
                log.warn("[AiServerWsProxy] capability call failed: capability={}, error={}", capability, errorMsg.get());
                return null;
            }
            String result = fullResponse.toString();
            log.info("[AiServerWsProxy] capability call success: capability={}, resultLen={}", capability, result.length());
            return result.isBlank() ? null : result;
        } catch (Exception e) {
            log.error("[AiServerWsProxy] capability call exception: capability={}, error={}", capability, e.getMessage());
            return null;
        }
    }

    /**
     * 生成教学资源（讲义/练习题/代码示例/思维导图/拓展阅读）。
     *
     * @param type        资源类型：lesson / quiz / code_case / mind_map / extended_reading
     * @param title       知识点名称
     * @param description 知识点描述
     * @return 生成的 Markdown 内容，失败返回 null
     */
    public String generateResource(String type, String title, String description) {
        Map<String, Object> config = Map.of(
                "prompt_type", type,
                "title", title != null ? title : "",
                "description", description != null ? description : title != null ? title : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 从对话中抽取学生画像（6维 + 知识点掌握）。
     */
    public String extractProfile(String dialogSummary) {
        Map<String, Object> config = Map.of(
                "prompt_type", "profile_extract",
                "dialog_summary", dialogSummary != null ? dialogSummary : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 对学习路径做个性化排序。
     */
    public String sortPath(List<String> kpIds, String profileHint) {
        Map<String, Object> config = Map.of(
                "prompt_type", "path_sort",
                "kp_ids", kpIds != null ? kpIds : List.of(),
                "profile_hint", profileHint != null ? profileHint : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 事实性校验（质检）。
     */
    public String qualityCheck(String title, String content) {
        Map<String, Object> config = Map.of(
                "prompt_type", "factual_check",
                "title", title != null ? title : "",
                "content", content != null ? content : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 薄弱点分析。
     */
    public String analyzeWeakness(String behaviorData) {
        Map<String, Object> config = Map.of(
                "prompt_type", "weakness_analysis",
                "behavior_data", behaviorData != null ? behaviorData : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 生成 Mermaid 流程图。
     */
    public String generateMermaid(String input) {
        Map<String, Object> config = Map.of(
                "prompt_type", "mermaid",
                "input", input != null ? input : ""
        );
        return callCapability("llm_generate", config);
    }

    /**
     * 将画像/学习状态同步到 ai-server 的 MemoryService（写入 PROFILE.md / SUMMARY.md）。
     * 通过 HTTP PUT /api/v1/memory 实现。
     */
    public void syncMemory(String file, String content) {
        try {
            String baseUrl = runtimeConfig.getAiServerBaseUrl();
            String url = baseUrl + "/api/v1/memory";
            String body = objectMapper.writeValueAsString(Map.of("file", file, "content", content != null ? content : ""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[AiServerWsProxy] 记忆同步成功: file={}", file);
            } else {
                log.warn("[AiServerWsProxy] 记忆同步失败: file={}, status={}", file, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("[AiServerWsProxy] 记忆同步异常: file={}, error={}", file, e.getMessage());
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
