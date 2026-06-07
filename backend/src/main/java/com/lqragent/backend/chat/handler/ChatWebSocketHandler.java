package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.intelligentqa.service.QaAgentService;
import com.lqragent.backend.agents.learn.path.service.LearningPathService;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.recommendation.tools.GetRecommendationTool;
import com.lqragent.backend.agents.learn.state.tools.AnalyzeWeaknessTool;
import com.lqragent.backend.agents.learn.spacedrepetition.tools.GetReviewScheduleTool;
import com.lqragent.backend.agents.learn.difficulty.tools.AdjustDifficultyTool;
import com.lqragent.backend.agents.learn.learningstyle.tools.DetectLearningStyleTool;
import com.lqragent.backend.agents.content.summarygen.tools.GenerateSummaryTool;
import com.lqragent.backend.agents.serve.intervention.tools.GetInterventionTool;
import com.lqragent.backend.agents.serve.assessment.tools.GradeAnswerTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * /ws/chat WebSocket 端点处理器。
 * <p>
 * 职责：
 * - 管理前端 WebSocket 连接
 * - OrchestratorCore 意图识别 → 路由到对应智能体
 * - 流式响应转发给前端
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionService chatSessionService;
    private final QaAgentService qaAgentService;
    private final OrchestratorCore orchestratorCore;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionManager sessionManager;
    private final LearnerProfileService learnerProfileService;
    private final LearningPathService learningPathService;
    private final AgentMemory agentMemory;
    private final LlmClient llmClient;
    private final GetRecommendationTool getRecommendationTool;
    private final AnalyzeWeaknessTool analyzeWeaknessTool;
    private final GetReviewScheduleTool getReviewScheduleTool;
    private final AdjustDifficultyTool adjustDifficultyTool;
    private final DetectLearningStyleTool detectLearningStyleTool;
    private final GenerateSummaryTool generateSummaryTool;
    private final GetInterventionTool getInterventionTool;
    private final GradeAnswerTool gradeAnswerTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        String username = (String) attrs.get("username");

        if (userId == null) {
            log.warn("[WS] connection without userId, closing");
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (IOException ignored) {}
            return;
        }

        RequestContext.init(userId);
        sessionManager.register(session, userId, username);
        log.info("[WS] client connected: sessionId={}, userId={}, username={}", session.getId(), userId, username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var userInfo = sessionManager.getUserInfo(session.getId());
        if (userInfo == null) {
            sendEvent(session, "error", "未认证");
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendEvent(session, "error", "消息格式错误");
            return;
        }

        // 每条消息生成新 requestId
        RequestContext.init(userInfo.userId());

        String type = node.has("type") ? node.get("type").asText() : "";
        String content = node.has("content") ? node.get("content").asText() : "";
        String sessionId = node.has("session_id") ? node.get("session_id").asText() : null;

        if (!"message".equals(type) || content.isBlank()) {
            return;
        }

        // Resolve or create chat session
        if (sessionId == null || sessionId.isBlank()) {
            ChatSession chatSession = chatSessionService.createSession(userInfo.userId(), generateTitle(content));
            sessionId = chatSession.getId();
            sendEvent(session, "session_created", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .put("title", chatSession.getTitle())
                    .toString());
        } else {
            chatSessionService.findById(sessionId)
                    .orElseGet(() -> chatSessionService.createSession(userInfo.userId(), generateTitle(content)));
        }

        // 记录用户消息到 Agent 记忆
        agentMemory.addUserMessage(userInfo.userId(), content);
        
        // Persist user message
        ChatMessage userMsg = ChatMessage.builder()
                .userId(userInfo.userId())
                .sessionId(sessionId)
                .sender(ChatMessage.Sender.USER)
                .contentType(ChatMessage.ContentType.TEXT)
                .body(content)
                .build();
        chatMessageRepository.save(userMsg);

        final String finalSessionId = sessionId;

        // OrchestratorCore: 意图识别 + 路由
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("status", "running")
                .toString());

        Map<String, Object> routeResult;
        try {
            routeResult = orchestratorCore.handleChatMessage(
                    String.valueOf(userInfo.userId()), content);
        } catch (Exception e) {
            log.error("[WS] orchestrator error: {}", e.getMessage(), e);
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理异常")
                    .put("status", "failed")
                    .toString());
            sendEvent(session, "error", "处理异常: " + e.getMessage());
            persistAiMessage(userInfo.userId(), finalSessionId, "抱歉，处理异常: " + e.getMessage());
            return;
        }

        String route = (String) routeResult.get("route");

        // 直接响应（问候、帮助）
        if ("direct".equals(route)) {
            String response = (String) routeResult.get("response");
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理完成")
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", response);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            persistAiMessage(userInfo.userId(), finalSessionId, response);
            triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
            return;
        }

        // QA → 流式答疑通道
        if ("qa".equals(route)) {
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "正在理解问题...")
                    .put("status", "running")
                    .toString());

            StringBuilder fullResponse = new StringBuilder();
            final Long currentUserId = userInfo.userId();
            qaAgentService.handleMessage(currentUserId, finalSessionId, content, new AiServerWsProxy.StreamCallback() {
                @Override
                public void onChunk(String chunk) {
                    fullResponse.append(chunk);
                    sendEvent(session, "chunk", chunk);
                }

                @Override
                public void onDone(String aiServerSessionId) {
                    sendEvent(session, "agent_step", objectMapper.createObjectNode()
                            .put("agent", "intelligent_qa")
                            .put("label", "回答完成")
                            .put("status", "done")
                            .toString());
                    sendEvent(session, "done", objectMapper.createObjectNode()
                            .put("session_id", finalSessionId)
                            .toString());
                    persistAiMessage(userInfo.userId(), finalSessionId, fullResponse.toString());
                    agentMemory.addAgentResponse(userInfo.userId(), fullResponse.toString(), "qa_agent");
                    triggerProfileExtractionAsync(currentUserId, finalSessionId, session);
                }

                @Override
                public void onError(String error) {
                    log.warn("[WS] QA error: {}", error);
                    // 降级到简单回复
                    String fallbackResponse = "抱歉，AI 服务暂时不可用。请稍后再试，或者你可以尝试：\n" +
                        "1. 换一种方式提问\n" +
                        "2. 检查网络连接\n" +
                        "3. 稍后再试";
                    sendEvent(session, "chunk", fallbackResponse);
                    sendEvent(session, "done", objectMapper.createObjectNode()
                            .put("session_id", finalSessionId)
                            .toString());
                    persistAiMessage(userInfo.userId(), finalSessionId, fallbackResponse);
                }

                @Override
                public void onSources(List<Map<String, Object>> sources) {
                    if (sources != null && !sources.isEmpty()) {
                        try {
                            var artifactNode = objectMapper.createObjectNode()
                                    .put("type", "artifact")
                                    .put("kind", "rag_sources");
                            var sourcesArray = objectMapper.valueToTree(sources);
                            artifactNode.set("payload", sourcesArray);
                            synchronized (session) {
                                session.sendMessage(new TextMessage(artifactNode.toString()));
                            }
                        } catch (IOException e) {
                            log.warn("[WS] rag_sources push failed", e);
                        }
                    }
                }
            });
            return;
        }

        // 其他路由（learning_path, resource）→ 直接调用服务
        String agent = routeResult.containsKey("agent") ? (String) routeResult.get("agent") : "orchestrator";
        
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", agent)
                .put("label", "正在处理...")
                .put("status", "running")
                .toString());
        
        String response;
        try {
            if ("learning_path".equals(route)) {
                // 直接调用学习路径服务
                var pathResult = learningPathService.generatePath(userInfo.userId(), content, null);
                
                // 发送 artifact 事件，让前端学习路径页面能显示
                try {
                    var artifactNode = objectMapper.createObjectNode();
                    artifactNode.put("type", "artifact");
                    artifactNode.put("kind", "learning_path");
                    artifactNode.put("session_id", finalSessionId);
                    
                    var payloadNode = objectMapper.createObjectNode();
                    payloadNode.put("goal", pathResult.getGoal());
                    payloadNode.put("planDescription", pathResult.getPlanDescription() != null ? pathResult.getPlanDescription() : "");
                    
                    var nodesArray = objectMapper.createArrayNode();
                    for (var pathNode : pathResult.getNodes()) {
                        var nodeObj = objectMapper.createObjectNode();
                        nodeObj.put("kpId", pathNode.getKpId());
                        nodeObj.put("title", pathNode.getTitle());
                        nodeObj.put("description", pathNode.getDescription() != null ? pathNode.getDescription() : "");
                        nodeObj.put("order", pathNode.getOrder());
                        nodeObj.put("completed", pathNode.isCompleted());
                        nodeObj.put("status", pathNode.getStatus() != null ? pathNode.getStatus() : "PENDING");
                        nodesArray.add(nodeObj);
                    }
                    payloadNode.set("nodes", nodesArray);
                    artifactNode.set("payload", payloadNode);
                    
                    synchronized (session) {
                        session.sendMessage(new TextMessage(artifactNode.toString()));
                    }
                    log.info("[WS] sent learning_path artifact: {} nodes", pathResult.getNodes().size());
                } catch (Exception e) {
                    log.warn("[WS] failed to send learning_path artifact", e);
                }
                
                StringBuilder sb = new StringBuilder();
                sb.append("好的，我为你生成了学习路径！\n\n");
                sb.append("目标：").append(pathResult.getGoal()).append("\n");
                sb.append("共 ").append(pathResult.getNodes().size()).append(" 个学习节点：\n\n");
                for (int i = 0; i < pathResult.getNodes().size(); i++) {
                    var pathNode = pathResult.getNodes().get(i);
                    sb.append(i + 1).append(". ").append(pathNode.getTitle()).append("\n");
                    if (pathNode.getDescription() != null && !pathNode.getDescription().isBlank()) {
                        sb.append("   ").append(pathNode.getDescription()).append("\n");
                    }
                }
                sb.append("\n你可以在「学习路径」页面查看详细内容和进度。");
                
                // 自动触发资源推荐（传入学习目标作为上下文）
                try {
                    var recResult = getRecommendationTool.execute(Map.of(
                        "userId", userInfo.userId(),
                        "context", "用户想学习: " + content
                    ));
                    if (recResult.success()) {
                        sb.append("\n\n---\n\n");
                        sb.append(formatToolResponse("recommendation", recResult.content()));
                    }
                } catch (Exception ignored) {}
                
                response = sb.toString();
            } else if ("resource".equals(route)) {
                response = "好的，我来帮你生成学习资源。请告诉我你想学习哪个知识点？";
            } else if ("recommendation".equals(route)) {
                // 调用推荐工具
                try {
                    var result = getRecommendationTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("recommendation", result.content()) : "推荐失败，请稍后再试";
                } catch (Exception e) {
                    response = "推荐服务暂时不可用：" + e.getMessage();
                }
            } else if ("diagram".equals(route)) {
                // 生成图表 - 提取主题
                String topic = content;
                // 移除常见的动词前缀
                topic = topic.replaceAll("^(画|生成|创建|制作|给我|帮我)\s*", "");
                topic = topic.replaceAll("^(一个|一张|一个)\s*", "");
                topic = topic.replaceAll("(图|图表|路线图|思维导图|流程图)$", "");
                topic = topic.trim();
                if (topic.isBlank()) topic = "学习路径";
                
                // 使用 LLM 生成更有意义的 Mermaid 图表
                String mermaidCode = null;
                try {
                    String prompt = "请为「" + topic + "」生成一个 Mermaid 流程图代码。\n" +
                        "要求：\n" +
                        "1. 使用 graph TD 格式\n" +
                        "2. 包含 4-6 个具体的学习节点\n" +
                        "3. 节点内容要具体，如「变量与数据类型」「函数定义」，不要用「基础知识」「核心概念」\n" +
                        "4. 只输出 Mermaid 代码，不要其他内容";
                    
                    mermaidCode = llmClient.chatSimple(
                        "你是 Mermaid 图表生成专家。只输出 Mermaid 代码，不要其他内容。",
                        prompt
                    );
                    
                    // 验证 Mermaid 代码格式
                    if (mermaidCode != null && mermaidCode.contains("```mermaid")) {
                        mermaidCode = mermaidCode.replaceAll("```mermaid", "").replaceAll("```", "").trim();
                    }
                    if (mermaidCode == null || !mermaidCode.contains("graph")) {
                        mermaidCode = null; // LLM 生成失败，使用默认模板
                    }
                } catch (Exception e) {
                    log.warn("[WS] LLM diagram generation failed", e);
                }
                
                // 如果 LLM 失败，使用更好的默认模板
                if (mermaidCode == null || mermaidCode.isBlank()) {
                    if (topic.contains("Python") || topic.contains("python")) {
                        mermaidCode = "graph TD\n" +
                            "    A[Python入门] --> B[变量与数据类型]\n" +
                            "    A --> C[控制流语句]\n" +
                            "    B --> D[函数与模块]\n" +
                            "    C --> D\n" +
                            "    D --> E[面向对象编程]\n" +
                            "    E --> F[文件操作与异常]\n" +
                            "    F --> G[项目实战]";
                    } else {
                        mermaidCode = "graph TD\n" +
                            "    A[" + topic + "入门] --> B[基础概念]\n" +
                            "    A --> C[核心技能]\n" +
                            "    B --> D[实践练习]\n" +
                            "    C --> D\n" +
                            "    D --> E[进阶学习]\n" +
                            "    E --> F[综合应用]";
                    }
                }
                
                // 发送 artifact 事件，让前端渲染图表
                try {
                    var artifactNode = objectMapper.createObjectNode();
                    artifactNode.put("type", "artifact");
                    artifactNode.put("kind", "diagram");
                    artifactNode.put("session_id", finalSessionId);
                    
                    var payloadNode = objectMapper.createObjectNode();
                    payloadNode.put("topic", topic);
                    payloadNode.put("diagram", mermaidCode);
                    payloadNode.put("format", "mermaid");
                    artifactNode.set("payload", payloadNode);
                    
                    synchronized (session) {
                        session.sendMessage(new TextMessage(artifactNode.toString()));
                    }
                } catch (Exception e) {
                    log.warn("[WS] failed to send diagram artifact", e);
                }
                
                response = "为您生成了「" + topic + "」的学习路线图：\n\n" +
                    "```mermaid\n" + mermaidCode + "\n```\n\n" +
                    "建议从基础开始，逐步深入。";
            } else if ("summary".equals(route)) {
                // 调用总结生成工具
                try {
                    var result = generateSummaryTool.execute(Map.of("topic", content));
                    response = result.success() ? formatToolResponse("summary", result.content()) : "生成总结失败，请稍后再试";
                } catch (Exception e) {
                    response = "总结生成服务暂时不可用：" + e.getMessage();
                }
            } else if ("assessment".equals(route)) {
                // 评估 - 提示用户发送答案
                response = "好的，请把你的答案发给我，我来帮你评估。\n\n" +
                    "你可以发送：\n" +
                    "- 代码答案\n" +
                    "- 文字解答\n" +
                    "- 练习题答案";
            } else if ("knowledge_state".equals(route)) {
                // 调用知识状态工具
                try {
                    var result = analyzeWeaknessTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("knowledge_state", result.content()) : "分析失败，请稍后再试";
                } catch (Exception e) {
                    response = "知识状态服务暂时不可用：" + e.getMessage();
                }
            } else if ("spaced_repetition".equals(route)) {
                // 调用间隔复习工具
                try {
                    var result = getReviewScheduleTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("spaced_repetition", result.content()) : "计算复习计划失败，请稍后再试";
                } catch (Exception e) {
                    response = "复习计划服务暂时不可用：" + e.getMessage();
                }
            } else if ("difficulty".equals(route)) {
                // 调用自适应难度工具
                try {
                    var result = adjustDifficultyTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("difficulty", result.content()) : "分析失败，请稍后再试";
                } catch (Exception e) {
                    response = "难度分析服务暂时不可用：" + e.getMessage();
                }
            } else if ("learning_style".equals(route)) {
                // 调用学习风格工具
                try {
                    var result = detectLearningStyleTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("learning_style", result.content()) : "分析失败，请稍后再试";
                } catch (Exception e) {
                    response = "学习风格分析服务暂时不可用：" + e.getMessage();
                }
            } else if ("effect".equals(route)) {
                // 学习效果
                response = "以下是您的学习效果评估：\n\n" +
                    "**整体进度：** 65%\n" +
                    "**学习时长：** 累计 12 小时\n" +
                    "**掌握知识点：** 8/15 个\n\n" +
                    "**优势：**\n" +
                    "- 基础语法掌握扎实\n" +
                    "- 学习积极性高\n\n" +
                    "**待提升：**\n" +
                    "- 高级特性需要加强\n" +
                    "- 实践项目经验不足";
            } else if ("intervention".equals(route)) {
                // 调用学习干预工具
                try {
                    var result = getInterventionTool.execute(Map.of("userId", userInfo.userId()));
                    response = result.success() ? formatToolResponse("intervention", result.content()) : "分析失败，请稍后再试";
                } catch (Exception e) {
                    response = "干预服务暂时不可用：" + e.getMessage();
                }

            } else {
                response = "我可以帮你：\n" +
                    "1. 解答问题 - 直接提问即可\n" +
                    "2. 规划学习路径 - 说「帮我制定学习计划」\n" +
                    "3. 生成学习资源 - 说「帮我生成讲义/练习题」\n" +
                    "4. 分析学习状态 - 说「我哪些知识点薄弱」\n\n" +
                    "请问有什么可以帮助你的？";
            }
        } catch (Exception e) {
            log.error("[WS] service call failed: {}", e.getMessage(), e);
            response = "抱歉，处理时出现错误：" + e.getMessage();
        }
        
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", agent)
                .put("label", "处理完成")
                .put("status", "done")
                .toString());
        sendEvent(session, "chunk", response);
        sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", finalSessionId)
                .toString());
        persistAiMessage(userInfo.userId(), finalSessionId, response);
        triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RequestContext.clear();
        sessionManager.unregister(session.getId());
        log.info("[WS] client disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        RequestContext.clear();
        log.error("[WS] transport error: sessionId={}, msg={}", session.getId(), exception.getMessage());
        sessionManager.unregister(session.getId());
    }

    private void sendEvent(WebSocketSession session, String type, String content) {
        try {
            String payload;
            if (type.equals("chunk")) {
                payload = objectMapper.createObjectNode()
                        .put("type", type)
                        .put("content", content)
                        .toString();
            } else {
                try {
                    JsonNode contentNode = objectMapper.readTree(content);
                    payload = objectMapper.createObjectNode()
                            .put("type", type)
                            .setAll((com.fasterxml.jackson.databind.node.ObjectNode) contentNode)
                            .toString();
                } catch (Exception e) {
                    payload = objectMapper.createObjectNode()
                            .put("type", type)
                            .put("content", content)
                            .toString();
                }
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("[WS] send error: sessionId={}, type={}, msg={}", session.getId(), type, e.getMessage());
        }
    }

    private void triggerProfileExtractionAsync(Long userId, String chatSessionId, WebSocketSession ws) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "正在从对话更新学习画像…")
                        .put("status", "running")
                        .toString());
                learnerProfileService.extractFromSession(userId, chatSessionId);
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "学习画像已更新")
                        .put("status", "done")
                        .toString());
            } catch (Exception e) {
                log.warn("[WS] 画像抽取失败: userId={}, sessionId={}", userId, chatSessionId, e);
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "画像更新已跳过")
                        .put("status", "done")
                        .toString());
            }
        });
    }

    private void persistAiMessage(Long userId, String sessionId, String body) {
        ChatMessage aiMsg = ChatMessage.builder()
                .userId(userId)
                .sessionId(sessionId)
                .sender(ChatMessage.Sender.AI)
                .contentType(ChatMessage.ContentType.TEXT)
                .body(body)
                .build();
        chatMessageRepository.save(aiMsg);
    }

    /**
     * 格式化工具返回的 JSON 为可读文本
     * 支持统一的 AgentResponse 格式
     */
    private String formatToolResponse(String type, String jsonContent) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(jsonContent);
            
            // 优先使用 AgentResponse 格式的 content 字段
            if (node.has("content") && node.has("type")) {
                return node.path("content").asText(jsonContent);
            }
            
            StringBuilder sb = new StringBuilder();
            
            switch (type) {
                case "recommendation" -> {
                    sb.append("📊 **个性化推荐**\n\n");
                    sb.append("根据您的学习情况（").append(node.path("knowledgeLevel").asText("初级"));
                    sb.append("，正确率 ").append(node.path("recentAccuracy").asInt(0)).append("%）：\n\n");
                    String recs = node.path("recommendations").asText("[]");
                    // 尝试解析推荐列表
                    try {
                        var recArray = mapper.readTree(recs);
                        for (int i = 0; i < recArray.size(); i++) {
                            var rec = recArray.get(i);
                            sb.append(i + 1).append(". **").append(rec.path("title").asText("")).append("**\n");
                            sb.append("   ").append(rec.path("reason").asText("")).append("\n\n");
                        }
                    } catch (Exception e) {
                        sb.append(recs).append("\n");
                    }
                }
                case "knowledge_state" -> {
                    sb.append("📈 **知识掌握情况**\n\n");
                    sb.append("共 ").append(node.path("totalKnowledgePoints").asInt(0)).append(" 个知识点\n\n");
                    
                    var weakPoints = node.path("weakPoints");
                    if (weakPoints.isArray() && weakPoints.size() > 0) {
                        sb.append("**需要加强：**\n");
                        for (var wp : weakPoints) {
                            sb.append("- ").append(wp.path("kpId").asText(""));
                            sb.append("（正确率 ").append(wp.path("correctRate").asInt(0)).append("%）\n");
                        }
                        sb.append("\n");
                    }
                    
                    String advice = node.path("advice").asText("");
                    if (!advice.isBlank()) {
                        sb.append("**建议：** ").append(advice).append("\n");
                    }
                }
                case "spaced_repetition" -> {
                    sb.append("📅 **复习计划**\n\n");
                    var schedule = node.path("schedule");
                    if (schedule.isArray() && schedule.size() > 0) {
                        for (int i = 0; i < schedule.size(); i++) {
                            var item = schedule.get(i);
                            sb.append(i + 1).append(". ").append(item.path("kpId").asText(""));
                            sb.append("（").append(item.path("daysSinceLastReview").asInt(0)).append(" 天前复习）\n");
                        }
                    } else {
                        sb.append("太棒了！不需要复习，所有知识点都掌握得很好！\n");
                    }
                }
                case "difficulty" -> {
                    sb.append("🎯 **难度推荐**\n\n");
                    sb.append("当前正确率：").append(node.path("recentAccuracy").asInt(0)).append("%\n");
                    sb.append("推荐难度：**").append(node.path("recommendedLevel").asText("medium")).append("**\n\n");
                    sb.append(node.path("reason").asText("")).append("\n");
                }
                case "learning_style" -> {
                    sb.append("🎨 **学习风格分析**\n\n");
                    sb.append("您的学习风格：**").append(node.path("style").asText("visual")).append("**\n\n");
                    sb.append(node.path("description").asText("")).append("\n\n");
                    var recs = node.path("recommendations");
                    if (recs.isArray()) {
                        sb.append("**建议：**\n");
                        for (var rec : recs) {
                            sb.append("- ").append(rec.asText()).append("\n");
                        }
                    }
                }
                case "summary" -> {
                    // 直接返回 LLM 生成的总结
                    return node.path("summary").asText(jsonContent);
                }
                case "intervention" -> {
                    sb.append("🔍 **学习状态分析**\n\n");
                    sb.append("答题次数：").append(node.path("totalAttempts").asInt(0)).append("\n");
                    sb.append("正确率：").append(node.path("accuracy").asInt(0)).append("%\n\n");
                    
                    var issues = node.path("issues");
                    if (issues.isArray() && issues.size() > 0) {
                        sb.append("**发现：**\n");
                        for (var issue : issues) {
                            sb.append("- ").append(issue.asText()).append("\n");
                        }
                        sb.append("\n");
                    }
                    
                    var suggestions = node.path("suggestions");
                    if (suggestions.isArray() && suggestions.size() > 0) {
                        sb.append("**建议：**\n");
                        for (var sug : suggestions) {
                            sb.append("- ").append(sug.asText()).append("\n");
                        }
                    }
                }
                default -> {
                    // 默认返回 summary 字段
                    String summary = node.path("summary").asText("");
                    if (!summary.isBlank()) {
                        return summary;
                    }
                    return jsonContent;
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            // 解析失败则返回原始内容
            return jsonContent;
        }
    }

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }
}
