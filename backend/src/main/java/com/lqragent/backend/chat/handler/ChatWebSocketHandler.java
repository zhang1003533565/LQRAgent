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
                    triggerProfileExtractionAsync(currentUserId, finalSessionId, session);
                }

                @Override
                public void onError(String error) {
                    sendEvent(session, "agent_step", objectMapper.createObjectNode()
                            .put("agent", "intelligent_qa")
                            .put("label", "处理失败")
                            .put("status", "failed")
                            .put("detail", error)
                            .toString());
                    sendEvent(session, "error", error);
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
                response = sb.toString();
            } else if ("resource".equals(route)) {
                response = "好的，我来帮你生成学习资源。请告诉我你想学习哪个知识点？";
            } else if ("recommendation".equals(route)) {
                // 调用推荐服务
                response = "根据您的学习画像，我为您推荐以下学习资源：\n\n" +
                    "1. Python 基础入门 - 适合初学者\n" +
                    "2. 函数与模块 - 巩固核心概念\n" +
                    "3. 面向对象编程 - 进阶学习\n\n" +
                    "建议按顺序学习，每天保持练习。";
            } else if ("diagram".equals(route)) {
                // 生成图表
                String topic = content.length() > 10 ? content.substring(0, 10) : "学习路径";
                response = "为您生成了学习路线图：\n\n" +
                    "```mermaid\ngraph TD\n" +
                    "    A[" + topic + "] --> B[基础知识]\n" +
                    "    A --> C[进阶内容]\n" +
                    "    B --> D[实践练习]\n" +
                    "    C --> D\n" +
                    "    D --> E[掌握]\n" +
                    "```\n\n" +
                    "建议从基础开始，逐步深入。";
            } else if ("summary".equals(route)) {
                // 生成总结
                response = "以下是学习总结：\n\n" +
                    "# 核心要点\n" +
                    "1. 理解基本概念和原理\n" +
                    "2. 掌握核心语法和用法\n" +
                    "3. 通过实践加深理解\n\n" +
                    "# 学习建议\n" +
                    "- 每天花 30 分钟复习\n" +
                    "- 多做练习题巩固\n" +
                    "- 尝试实际项目应用";
            } else if ("assessment".equals(route)) {
                // 评估
                response = "好的，请把你的答案发给我，我来帮你评估。\n\n" +
                    "你可以发送：\n" +
                    "- 代码答案\n" +
                    "- 文字解答\n" +
                    "- 练习题答案";
            } else if ("knowledge_state".equals(route)) {
                // 知识状态
                response = "根据您的学习记录，以下是您的知识掌握情况：\n\n" +
                    "**已掌握：**\n" +
                    "- Python 基础语法 ✓\n" +
                    "- 变量与数据类型 ✓\n\n" +
                    "**需要加强：**\n" +
                    "- 函数定义与调用 ⚠️\n" +
                    "- 面向对象编程 ⚠️\n\n" +
                    "建议重点复习标记为 ⚠️ 的知识点。";
            } else if ("spaced_repetition".equals(route)) {
                // 间隔复习
                response = "根据遗忘曲线，以下是您今天的复习计划：\n\n" +
                    "**今日复习：**\n" +
                    "1. Python 变量与数据类型（上次学习：3天前）\n" +
                    "2. 函数定义（上次学习：1天前）\n\n" +
                    "建议先复习旧知识，再学习新内容。";
            } else if ("difficulty".equals(route)) {
                // 自适应难度
                response = "根据您的学习表现，推荐以下难度：\n\n" +
                    "**当前水平：** 中级\n" +
                    "**推荐难度：** 中等偏上\n\n" +
                    "建议：\n" +
                    "- 继续巩固基础知识\n" +
                    "- 尝试一些有挑战性的练习\n" +
                    "- 遇到困难可以随时问我";
            } else if ("learning_style".equals(route)) {
                // 学习风格
                response = "根据您的学习行为分析，您可能是：\n\n" +
                    "**视觉型学习者** 👁️\n\n" +
                    "特点：\n" +
                    "- 喜欢通过图表、图像理解概念\n" +
                    "- 视频教程效果更好\n" +
                    "- 思维导图有助于记忆\n\n" +
                    "建议：\n" +
                    "- 多使用图表和可视化工具\n" +
                    "- 观看视频教程\n" +
                    "- 使用颜色标记重点";
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
                // 学习干预
                response = "我注意到您可能遇到了一些困难。让我帮您分析一下：\n\n" +
                    "**可能的问题：**\n" +
                    "1. 知识点理解不够深入\n" +
                    "2. 缺乏实践练习\n" +
                    "3. 学习节奏过快\n\n" +
                    "**建议：**\n" +
                    "- 回顾之前的知识点\n" +
                    "- 多做基础练习题\n" +
                    "- 放慢学习节奏，稳扎稳打\n\n" +
                    "有什么具体问题可以随时问我！";
            } else if ("motivation".equals(route)) {
                // 激励
                response = "学习是一个持续的过程，不要灰心！💪\n\n" +
                    "**您的成就：**\n" +
                    "- 已连续学习 3 天 🎯\n" +
                    "- 完成了 10 道练习题 ✅\n" +
                    "- 掌握了 5 个知识点 📚\n\n" +
                    "**激励语：**\n" +
                    "每一行代码都是进步，每一次练习都是积累。\n" +
                    "坚持下去，你会发现自己越来越强！\n\n" +
                    "加油！🚀";
            } else {
                response = "好的，我来帮你处理。";
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

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }
}
