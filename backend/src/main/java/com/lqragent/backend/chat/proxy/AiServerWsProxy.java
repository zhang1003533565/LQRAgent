package com.lqragent.backend.chat.proxy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class AiServerWsProxy {

    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper;
    /** 复用的 HttpClient 单例，避免每次调用都创建新连接 */
    private final HttpClient sharedHttpClient;
    private final LlmClient llmClient;

    // ───────── 新增改造相关的依赖 ─────────
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionService chatSessionService;
    private final LearnerProfileService learnerProfileService;

    /** 连接池：前端 WebSocket session ID → ai-server WebSocket */
    private final ConcurrentHashMap<String, WebSocket> aiConnections = new ConcurrentHashMap<>();
    /** 会话信息：前端 WebSocket session ID → {userId, chatSessionId, frontendWs} */
    private final ConcurrentHashMap<String, SessionInfo> sessionInfos = new ConcurrentHashMap<>();
    /** 响应缓冲区：前端 WebSocket session ID → 累积的 content 文本 */
    private final ConcurrentHashMap<String, StringBuilder> responseBuffers = new ConcurrentHashMap<>();
    /** 会话 ID 映射：chatSessionId → aiServerSessionId */
    private final ConcurrentHashMap<Long, String> sessionIdMap = new ConcurrentHashMap<>();

    public AiServerWsProxy(AppRuntimeConfig runtimeConfig,
                           LlmClient llmClient,
                           ChatMessageRepository chatMessageRepository,
                           ChatSessionService chatSessionService,
                           @Lazy LearnerProfileService learnerProfileService) {
        this.runtimeConfig = runtimeConfig;
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
        // 配置 HttpClient 支持 WebSocket
        this.sharedHttpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionService = chatSessionService;
        this.learnerProfileService = learnerProfileService;
    }

    // ========================================================================
    // 原有方法 — 被 LearningPathService / LearnerProfileService / ContentAnalyzerService
    //               / ResourceGenerationService / EffectAssessmentService / QaAgent
    //               / QualityAssessmentService / RagSearchTool / CheckQualityTool
    //               / GenerateDiagramTool / GenerateLessonTool / AnalyzeWeaknessTool 等依赖
    // ========================================================================

    /**
     * 流式聊天（旧 /api/v1/chat 端点，走 RAG 知识库）
     */
    public void streamChat(String sessionId, String userMessage, Long userId, StreamCallback callback) {
        String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        String privatePrefix = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-");
        String privateKb = privatePrefix + userId;
        streamChat(sessionId, userMessage, List.of(publicKb, privateKb), callback);
    }

    public void streamChat(String sessionId, String userMessage, List<String> knowledgeBases, StreamCallback callback) {
        String wsUrl = getChatWsUrl();
        int connectTimeoutSec = runtimeConfig.getWsConnectTimeoutSec();
        int responseTimeoutSec = runtimeConfig.getWsResponseTimeoutSec();
        log.info("[AiServerWsProxy] streamChat: wsUrl={} (connect={}s, response={}s)", wsUrl, connectTimeoutSec, responseTimeoutSec);

        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            AtomicReference<String> aiServerSessionId = new AtomicReference<>();

            WebSocket ws = sharedHttpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            String chineseInstruction = "[System Instruction: Please respond in Chinese (中文). Use Simplified Chinese for all responses.]\\n\\n";
                            var payloadNode = objectMapper.createObjectNode()
                                    .put("type", "message")
                                    .put("message", chineseInstruction + userMessage)
                                    .put("content", chineseInstruction + userMessage)
                                    .put("session_id", sessionId != null ? sessionId : "");
                            String primaryKb = null;
                            var kbArray = payloadNode.putArray("knowledge_bases");
                            for (String kb : knowledgeBases) {
                                if (kb != null && !kb.isBlank()) {
                                    if (primaryKb == null) primaryKb = kb;
                                    kbArray.add(kb);
                                }
                            }
                            if (primaryKb != null) {
                                payloadNode.put("enable_rag", true);
                                payloadNode.put("kb_name", primaryKb);
                                payloadNode.putArray("tools").add("rag");
                            }
                            webSocket.sendText(payloadNode.toString(), true);
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
                                    case "stream", "content" -> {
                                        String c = node.has("content") ? node.get("content").asText() : "";
                                        if (!c.isEmpty()) { fullResponse.append(c); callback.onChunk(c); }
                                    }
                                    case "thinking" -> {
                                        String t = node.has("content") ? node.get("content").asText() : "";
                                        if (!t.isBlank()) { fullResponse.append(t); callback.onChunk(t); }
                                    }
                                    case "sources" -> {
                                        try {
                                            List<Map<String, Object>> allSources = new java.util.ArrayList<>();
                                            if (node.has("metadata") && node.get("metadata").has("sources")) {
                                                allSources.addAll(objectMapper.convertValue(node.get("metadata").get("sources"), new TypeReference<>() {}));
                                            }
                                            if (node.has("sources") && node.get("sources").isArray()) {
                                                allSources.addAll(objectMapper.convertValue(node.get("sources"), new TypeReference<>() {}));
                                            }
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
                                            if (!allSources.isEmpty()) callback.onSources(allSources);
                                        } catch (Exception e) {
                                            log.warn("[AiServerWsProxy] parse sources error: {}", e.getMessage());
                                        }
                                    }
                                    case "result" -> {
                                        String r = node.has("content") ? node.get("content").asText() : "";
                                        if (!r.isBlank() && fullResponse.isEmpty()) { fullResponse.append(r); callback.onChunk(r); }
                                        callback.onDone(aiServerSessionId.get());
                                        doneLatch.countDown();
                                    }
                                    case "done" -> { callback.onDone(aiServerSessionId.get()); doneLatch.countDown(); }
                                    case "error" -> {
                                        String err = node.has("content") ? node.get("content").asText() : "AI server error";
                                        callback.onError(err);
                                        doneLatch.countDown();
                                    }
                                    case "session" -> { if (node.has("session_id")) aiServerSessionId.set(node.get("session_id").asText()); }
                                }
                            } catch (Exception e) {
                                log.debug("[AiServerWsProxy] non-JSON frame skipped");
                            }
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            callback.onError("AI 服务连接异常: " + error.getMessage());
                            doneLatch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            if (doneLatch.getCount() > 0) {
                                if (fullResponse.length() > 0) callback.onDone(aiServerSessionId.get());
                                else callback.onError("AI 服务连接已关闭");
                                doneLatch.countDown();
                            }
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    })
                    .get(connectTimeoutSec, TimeUnit.SECONDS);

            boolean completed = doneLatch.await(responseTimeoutSec, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("[AiServerWsProxy] streamChat timed out after {}s", responseTimeoutSec);
                if (fullResponse.length() > 0) callback.onDone(aiServerSessionId.get());
                else callback.onError("AI 服务响应超时");
            }
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
        } catch (Exception e) {
            log.error("[AiServerWsProxy] streamChat failed: {}", e.getMessage());
            callback.onError("无法连接 AI 服务: " + e.getMessage());
        }
    }

    /**
     * 通用 capability 调用（同步等待完整响应）。
     */
    public String callCapability(String capability, Map<String, Object> config) {
        return callCapability(capability, config, null);
    }

    public String callCapability(String capability, Map<String, Object> config, List<String> knowledgeBases) {
        String wsUrl = runtimeConfig.getAiServerWsUrl();
        int connectTimeoutSec = runtimeConfig.getWsConnectTimeoutSec();
        int responseTimeoutSec = runtimeConfig.getWsResponseTimeoutSec();
        log.info("[AiServerWsProxy] callCapability: capability={}, url={}", capability, wsUrl);

        try {
            CountDownLatch doneLatch = new CountDownLatch(1);
            StringBuilder fullResponse = new StringBuilder();
            AtomicReference<String> errorMsg = new AtomicReference<>();

            WebSocket ws = sharedHttpClient.newWebSocketBuilder()
                    .header("User-Agent", "LQRAgent/1.0")
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("[AiServerWsProxy] WebSocket connected");
                            webSocket.request(1);
                            // 延迟发送消息，确保连接完全建立
                            CompletableFuture.runAsync(() -> {
                                try {
                                    var payloadNode = objectMapper.createObjectNode()
                                            .put("type", "message")
                                            .put("capability", capability)
                                            .put("content", "")
                                            .put("session_id", "");
                                    if (config != null && !config.isEmpty()) {
                                        payloadNode.set("config", objectMapper.valueToTree(config));
                                    }
                                    if (knowledgeBases != null && !knowledgeBases.isEmpty()) {
                                        var kbArray = payloadNode.putArray("knowledge_bases");
                                        for (String kb : knowledgeBases) {
                                            if (kb != null && !kb.isBlank()) kbArray.add(kb);
                                        }
                                    }
                                    String msg = payloadNode.toString();
                                    log.info("[AiServerWsProxy] sending message: {}", msg.substring(0, Math.min(200, msg.length())));
                                    webSocket.sendText(msg, true);
                                } catch (Exception e) {
                                    log.error("[AiServerWsProxy] send message failed: {}", e.getMessage());
                                }
                            });
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            String text = data.toString();
                            log.debug("[AiServerWsProxy] received: {}", text.substring(0, Math.min(200, text.length())));
                            try {
                                JsonNode node = objectMapper.readTree(text);
                                if (!node.has("type")) return WebSocket.Listener.super.onText(webSocket, data, last);
                                switch (node.get("type").asText()) {
                                    case "content", "stream" -> {
                                        String c = node.has("content") ? node.get("content").asText() : "";
                                        if (!c.isEmpty()) fullResponse.append(c);
                                    }
                                    case "result" -> {
                                        String r = node.has("content") ? node.get("content").asText() : "";
                                        if (!r.isBlank() && fullResponse.isEmpty()) fullResponse.append(r);
                                        doneLatch.countDown();
                                    }
                                    case "done" -> doneLatch.countDown();
                                    case "error" -> { errorMsg.set(node.has("content") ? node.get("content").asText() : "Unknown error"); doneLatch.countDown(); }
                                }
                            } catch (Exception ignored) {}
                            webSocket.request(1);
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("[AiServerWsProxy] WebSocket error: {}", error.getMessage());
                            errorMsg.set("WS error: " + error.getMessage());
                            doneLatch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("[AiServerWsProxy] WebSocket closed: status={}, reason={}", statusCode, reason);
                            if (doneLatch.getCount() > 0) {
                                if (fullResponse.length() > 0) doneLatch.countDown();
                                else errorMsg.set("Connection closed");
                            }
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    })
                    .get(connectTimeoutSec, TimeUnit.SECONDS);

            boolean completed = doneLatch.await(responseTimeoutSec, TimeUnit.SECONDS);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
            if (!completed) {
                log.warn("[AiServerWsProxy] callCapability timeout: capability={}", capability);
                return null;
            }
            if (errorMsg.get() != null) {
                log.warn("[AiServerWsProxy] callCapability error: capability={}, error={}", capability, errorMsg.get());
                return null;
            }
            String result = fullResponse.toString();
            return result.isBlank() ? null : result;
        } catch (Exception e) {
            log.error("[AiServerWsProxy] callCapability exception: capability={}, error={}", capability, e.getMessage());
            return null;
        }
    }

    /** 生成教学资源 */
    public String generateResource(String type, String title, String description) {
        String prompt = String.format(
                "你是一个学习资源生成专家。请根据以下信息生成%s类型的学习资源。\n标题：%s\n描述：%s\n\n请生成结构化的学习资源内容。",
                type, title != null ? title : "", description != null ? description : "");
        return callLlmDirect(prompt);
    }

    /** 从对话中抽取学生画像 */
    public String extractProfile(String dialogSummary) {
        String prompt = String.format(
                "请从以下对话记录中抽取学生的学习画像，包括：知识水平、学习风格、薄弱点、学习目标。\n\n对话记录：\n%s\n\n请以JSON格式输出画像信息。",
                dialogSummary != null ? dialogSummary : "");
        return callLlmDirect(prompt);
    }

    /** 对学习路径做个性化排序 */
    public String sortPath(List<String> kpIds, String profileHint) {
        String prompt = String.format(
                "请根据学生画像对以下知识点进行个性化排序，给出最佳学习顺序。\n知识点列表：%s\n学生画像：%s\n\n请输出排序后的知识点ID列表。",
                kpIds != null ? String.join(", ", kpIds) : "",
                profileHint != null ? profileHint : "");
        return callLlmDirect(prompt);
    }

    /** 事实性校验 */
    public String qualityCheck(String title, String content) {
        String prompt = String.format(
                "请检查以下学习内容的事实准确性。如果发现错误，请指出并说明正确信息。\n\n标题：%s\n内容：%s\n\n如果内容正确，请回复'PASS'。如果发现错误，请回复'FAIL: 错误描述'。",
                title != null ? title : "", content != null ? content : "");
        return callLlmDirect(prompt);
    }

    /** 薄弱点分析 */
    public String analyzeWeakness(String behaviorData) {
        String prompt = String.format(
                "请根据以下学生学习行为数据分析其薄弱知识点和学习问题。\n\n学习行为数据：\n%s\n\n请分析并输出：1. 薄弱知识点列表 2. 学习问题分析 3. 改进建议。",
                behaviorData != null ? behaviorData : "");
        return callLlmDirect(prompt);
    }

    /** 生成 Mermaid 流程图 */
    public String generateMermaid(String input) {
        String prompt = String.format(
                "请根据以下内容生成Mermaid流程图代码。\n\n内容：%s\n\n请只输出Mermaid代码，不要包含其他说明。",
                input != null ? input : "");
        return callLlmDirect(prompt);
    }

    /** 直接调用 LLM（用于简单任务） */
    private String callLlmDirect(String userMessage) {
        try {
            var response = llmClient.chat(
                    "你是一个专业的教育助手，请根据用户要求完成任务。",
                    List.of(Map.of("role", "user", "content", userMessage)),
                    null
            );
            if (response.isSuccess()) {
                return response.content();
            } else {
                log.warn("[AiServerWsProxy] callLlmDirect failed: {}", response.error());
                return null;
            }
        } catch (Exception e) {
            log.error("[AiServerWsProxy] callLlmDirect exception: {}", e.getMessage());
            return null;
        }
    }

    /** 同步记忆到 ai-server 的 MemoryService */
    public void syncMemory(String file, String content) {
        try {
            String baseUrl = runtimeConfig.getAiServerBaseUrl();
            String body = objectMapper.writeValueAsString(Map.of(
                    "file", file,
                    "content", content != null ? content : ""));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/memory"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[AiServerWsProxy] syncMemory success: file={}", file);
            } else {
                log.warn("[AiServerWsProxy] syncMemory failed: file={}, status={}", file, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("[AiServerWsProxy] syncMemory exception: file={}", file, e);
        }
    }

    // ========================================================================
    // 新增：Agentic Pipeline 入口（对接 ai-server 的 /api/v1/ws 新版 WebSocket）
    // ========================================================================

    /**
     * 启动一次 AI 对话处理（走 ai-server 的 Agentic Pipeline）。
     * ChatWebSocketHandler 收到前端消息后调用，后续事件自动转发到前端。
     */
    public void startSession(String frontendSessionId, Long userId, Long chatSessionId,
                             String userMessage, String chatHistory, String learnerProfile,
                             WebSocketSession frontendWs) {
        String aiSessionId = "user_" + userId + "_session_" + chatSessionId;
        sessionInfos.put(frontendSessionId, new SessionInfo(userId, chatSessionId, frontendWs));
        sessionIdMap.put(chatSessionId, aiSessionId);
        chatSessionService.updateAiServerSessionId(chatSessionId, aiSessionId);
        saveUserMessage(chatSessionId, userId, userMessage);
        responseBuffers.put(frontendSessionId, new StringBuilder());

        sendJsonToFrontend(frontendWs, createAgentStepEvent("orchestrator", "正在分析问题...", "running"));

        WebSocket existing = aiConnections.get(frontendSessionId);
        if (existing != null && !existing.isInputClosed()) {
            sendTurnMessage(existing, aiSessionId, userMessage, chatHistory, learnerProfile);
        } else {
            connectAndRun(frontendSessionId, aiSessionId, userMessage, chatHistory, learnerProfile, frontendWs);
        }
    }

    /** 前端断开时清理 */
    public void onFrontendDisconnected(String frontendSessionId) {
        WebSocket aiWs = aiConnections.remove(frontendSessionId);
        if (aiWs != null && !aiWs.isInputClosed()) {
            aiWs.sendClose(1000, "Frontend disconnected");
        }
        sessionInfos.remove(frontendSessionId);
        responseBuffers.remove(frontendSessionId);
    }

    /** 建立新连接并发消息 */
    private void connectAndRun(String frontendSessionId, String aiSessionId,
                                String userMessage, String chatHistory, String learnerProfile,
                                WebSocketSession frontendWs) {
        try {
            URI uri = URI.create(runtimeConfig.getAiServerWsUrl());
            sharedHttpClient.newWebSocketBuilder()
                    .buildAsync(uri, new AiEventAdapter(frontendSessionId))
                    .orTimeout(runtimeConfig.getWsConnectTimeoutSec(), TimeUnit.SECONDS)
                    .thenAccept(aiWs -> {
                        aiConnections.put(frontendSessionId, aiWs);
                        sendTurnMessage(aiWs, aiSessionId, userMessage, chatHistory, learnerProfile);
                    })
                    .exceptionally(ex -> {
                        log.error("[AiServerWsProxy] connect failed: {}", ex.getMessage());
                        sendJsonToFrontend(frontendWs, createErrorEvent("AI 服务连接失败"));
                        sessionInfos.remove(frontendSessionId);
                        return null;
                    });
        } catch (Exception e) {
            log.error("[AiServerWsProxy] connect exception: {}", e.getMessage());
            sendJsonToFrontend(frontendWs, createErrorEvent("AI 服务连接异常"));
            sessionInfos.remove(frontendSessionId);
        }
    }

    /** 在已有连接上发送 turn 消息 */
    private void sendTurnMessage(WebSocket aiWs, String aiSessionId,
                                  String userMessage, String chatHistory, String learnerProfile) {
        // 把用户画像拼到消息内容里（chat 能力的 config 不接受自定义字段）
        String enrichedContent = userMessage;
        if (learnerProfile != null && !learnerProfile.isBlank()) {
            enrichedContent = "[用户画像: " + learnerProfile + "]\n" + userMessage;
        }
        // 历史记录由 ai-server 从 session 自动加载（session_id 相同即可）

        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "message");
        root.put("content", enrichedContent);
        root.put("session_id", aiSessionId);
        root.put("capability", "chat");
        root.put("language", "zh");
        var tools = root.putArray("tools");
        tools.add("rag");
        tools.add("web_search");
        tools.add("code_execution");
        var kbs = root.putArray("knowledge_bases");
        kbs.add("kb-public");

        aiWs.sendText(root.toString(), true);
        log.info("[AiServerWsProxy] turn sent: session={}, msgLen={}", aiSessionId, enrichedContent.length());
    }

    // ───────── ai-server 事件适配器 ─────────

    private class AiEventAdapter implements WebSocket.Listener {
        private final String frontendSessionId;
        private final StringBuilder partialJson = new StringBuilder();
        private boolean turnCompleted = false;

        AiEventAdapter(String frontendSessionId) { this.frontendSessionId = frontendSessionId; }

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("[AiServerWsProxy] ai-server connected: session={}", frontendSessionId);
            webSocket.request(1); // 请求接收下一条消息
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialJson.append(data);
            if (!last) {
                webSocket.request(1);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            String fullText = partialJson.toString();
            partialJson.setLength(0);
            try {
                handleEvent(objectMapper.readTree(fullText));
            } catch (Exception e) {
                log.warn("[AiServerWsProxy] parse error: {}", e.getMessage());
            }
            webSocket.request(1); // 请求下一条消息
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[AiServerWsProxy] ai-server closed: session={}", frontendSessionId);
            aiConnections.remove(frontendSessionId, webSocket);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("[AiServerWsProxy] ai-server error: {}", error.getMessage());
            SessionInfo info = sessionInfos.get(frontendSessionId);
            if (info != null && !turnCompleted) {
                sendJsonToFrontend(info.frontendWs, createErrorEvent("AI 服务通信异常"));
            }
            cleanupConnection(frontendSessionId);
        }

        private void handleEvent(JsonNode event) {
            if (turnCompleted) return;
            String type = event.has("type") ? event.get("type").asText() : "";
            String content = event.has("content") ? event.get("content").asText() : "";
            SessionInfo info = sessionInfos.get(frontendSessionId);
            if (info == null) return;

            switch (type) {
                case "session" -> log.debug("[AiServerWsProxy] session ready");
                case "thinking" -> { /* 思考过程暂不展示 */ }
                case "progress" -> {
                    String stage = event.has("stage") ? event.get("stage").asText() : "";
                    sendJsonToFrontend(info.frontendWs,
                            createAgentStepEvent(mapStageToAgent(stage), mapStageToLabel(stage, content), "running"));
                }
                case "content" -> {
                    if (!content.isEmpty()) {
                        StringBuilder buf = responseBuffers.get(frontendSessionId);
                        if (buf != null) buf.append(content);
                        sendJsonToFrontend(info.frontendWs, createChunkEvent(content));
                    }
                }
                case "sources" -> {
                    JsonNode sources = event.has("metadata") && event.get("metadata").has("sources")
                            ? event.get("metadata").get("sources") : null;
                    if (sources != null && sources.isArray() && sources.size() > 0) {
                        sendJsonToFrontend(info.frontendWs, createArtifactEvent("rag_sources", sources));
                    }
                }
                case "done" -> {
                    turnCompleted = true;
                    finishTurn(info);
                }
                case "error" -> {
                    log.warn("[AiServerWsProxy] ai-server error: {}", content);
                    sendJsonToFrontend(info.frontendWs, createErrorEvent(content.isEmpty() ? "AI 处理出错" : content));
                }
            }
        }

        private void finishTurn(SessionInfo info) {
            StringBuilder buf = responseBuffers.remove(frontendSessionId);
            String fullResponse = (buf != null) ? buf.toString() : "";

            if (!fullResponse.isEmpty()) {
                saveAssistantMessage(info.chatSessionId, info.userId, fullResponse);
            }
            sendJsonToFrontend(info.frontendWs, createAgentStepEvent("orchestrator", "处理完成", "done"));
            sendJsonToFrontend(info.frontendWs, createDoneEvent());

            if (!fullResponse.isEmpty()) {
                triggerProfileExtractionAsync(info.userId, info.chatSessionId, info.frontendWs);
            }
            log.info("[AiServerWsProxy] turn done: session={}, chars={}", frontendSessionId, fullResponse.length());
        }
    }

    // ───────── 事件翻译 ─────────

    private String mapStageToLabel(String stage, String fallback) {
        if (fallback != null && !fallback.isBlank()) return fallback;
        return switch (stage) {
            case "planning" -> "正在规划...";
            case "thinking" -> "正在思考...";
            case "acting" -> "正在查询资料...";
            case "observing" -> "正在分析结果...";
            case "responding", "writing" -> "正在生成回答...";
            case "reasoning" -> "正在推理...";
            default -> "处理中...";
        };
    }

    private String mapStageToAgent(String stage) {
        return switch (stage) {
            case "planning", "thinking" -> "planner";
            case "acting" -> "tool_executor";
            case "observing" -> "observer";
            case "responding", "writing" -> "writer";
            case "reasoning" -> "reasoner";
            default -> "orchestrator";
        };
    }

    // ───────── JSON 构建 ─────────

    private ObjectNode createAgentStepEvent(String agent, String label, String status) {
        return objectMapper.createObjectNode()
                .put("type", "agent_step")
                .put("agent", agent)
                .put("label", label)
                .put("status", status);
    }

    private ObjectNode createChunkEvent(String content) {
        return objectMapper.createObjectNode()
                .put("type", "chunk")
                .put("content", content);
    }

    private ObjectNode createArtifactEvent(String kind, JsonNode payload) {
        return objectMapper.createObjectNode()
                .put("type", "artifact")
                .put("kind", kind)
                .set("payload", payload);
    }

    private ObjectNode createDoneEvent() {
        return objectMapper.createObjectNode().put("type", "done");
    }

    private ObjectNode createErrorEvent(String content) {
        return objectMapper.createObjectNode()
                .put("type", "error")
                .put("content", content);
    }

    private void sendJsonToFrontend(WebSocketSession session, ObjectNode json) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json.toString()));
            }
        } catch (Exception e) {
            log.warn("[AiServerWsProxy] send failed: {}", e.getMessage());
        }
    }

    // ───────── 数据库操作 ─────────

    private void saveUserMessage(Long chatSessionId, Long userId, String content) {
        try {
            chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(chatSessionId).userId(userId)
                    .role("user").content(content)
                    .contentType("text").agentName("user")
                    .createdAt(LocalDateTime.now()).build());
        } catch (Exception e) {
            log.warn("[AiServerWsProxy] save user msg failed: {}", e.getMessage());
        }
    }

    private void saveAssistantMessage(Long chatSessionId, Long userId, String content) {
        try {
            chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(chatSessionId).userId(userId)
                    .role("assistant").content(content)
                    .contentType("text").agentName("ai-server")
                    .createdAt(LocalDateTime.now()).build());
            log.info("[AiServerWsProxy] saved assistant: sessionId={}, chars={}", chatSessionId, content.length());
        } catch (Exception e) {
            log.warn("[AiServerWsProxy] save assistant msg failed: {}", e.getMessage());
        }
    }

    private void triggerProfileExtractionAsync(Long userId, Long chatSessionId, WebSocketSession frontendWs) {
        Thread updater = new Thread(() -> {
            try {
                sendJsonToFrontend(frontendWs, createAgentStepEvent("learner_profile", "正在从对话更新学习画像...", "running"));
                learnerProfileService.extractFromSession(userId, String.valueOf(chatSessionId));
                sendJsonToFrontend(frontendWs, createAgentStepEvent("learner_profile", "学习画像已更新", "done"));
            } catch (Exception e) {
                log.warn("[AiServerWsProxy] profile extraction failed: {}", e.getMessage());
            }
        }, "profile-extract-" + chatSessionId);
        updater.setDaemon(true);
        updater.start();
    }

    private void cleanupConnection(String frontendSessionId) {
        WebSocket ws = aiConnections.remove(frontendSessionId);
        if (ws != null && !ws.isInputClosed()) ws.sendClose(1000, "Cleanup");
        sessionInfos.remove(frontendSessionId);
        responseBuffers.remove(frontendSessionId);
    }

    // ───────── 内部类型 ─────────

    private record SessionInfo(Long userId, Long chatSessionId, WebSocketSession frontendWs) {}

    /**
     * 流式回调（原有 streamChat 使用）
     */
    public interface StreamCallback {
        void onChunk(String content);
        void onDone(String aiServerSessionId);
        void onError(String error);
        default void onSources(List<Map<String, Object>> sources) {}
    }

    /** 获取旧的 /api/v1/chat 端点 URL */
    private String getChatWsUrl() {
        String base = runtimeConfig.getAiServerBaseUrl();
        if (base.startsWith("https://")) return "wss://" + base.substring("https://".length()) + "/api/v1/chat";
        if (base.startsWith("http://")) return "ws://" + base.substring("http://".length()) + "/api/v1/chat";
        return "ws://localhost:8001/api/v1/chat";
    }
}
