package com.lqragent.backend.orchestrator;

import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import com.lqragent.backend.agents.base.LlmClient;
import lombok.extern.slf4j.Slf4j;
import com.lqragent.backend.chat.service.AgentRunLogService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator 调度中枢
 * 接收前端请求 → 拆分任务 → 分发到各智能体 → 监听结果 → 聚合返回
 */
@Slf4j
@Service("orchestratorCore")
public class OrchestratorCore {

    private final RedisStreamsService streams;
    private final AgentRunLogService runLogService;
    private final LlmClient llmClient;

    // 任务状态跟踪
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    public OrchestratorCore(RedisStreamsService streams, AgentRunLogService runLogService, LlmClient llmClient) {
        this.streams = streams;
        this.runLogService = runLogService;
        this.llmClient = llmClient;
    }

    /**
     * 主入口：处理用户学习请求
     */
    public String handleLearnRequest(String userId, String goal, WebSocketSession session) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(taskId, userId, goal, session);
        tasks.put(taskId, state);

        log.info("[Orchestrator] new task: {}, goal={}", taskId, goal);

        // Step 1: 并行发送到画像 + 路径规划
        sendTask(taskId, AgentIds.PROFILE, "get_profile", Map.of("userId", userId));
        sendTask(taskId, AgentIds.LEARNING_PATH, "generate_path", Map.of("goal", goal, "userId", userId));

        // 启动结果监听
        startResultListener(taskId);

        return taskId;
    }

    /**
     * 发送任务到指定智能体
     */
    private void sendTask(String taskId, String agentId, String action, Map<String, Object> payload) {
        Map<String, Object> content = new HashMap<>(payload);
        content.put("action", action);
        AgentMessage msg = AgentMessage.request(taskId, AgentIds.ORCHESTRATOR, agentId, content);
        streams.send("stream:agent:" + agentId, msg);
        log.info("[Orchestrator] sent task to {}: {}", agentId, action);
    }

    /**
     * 启动结果监听线程
     */
    private void startResultListener(String taskId) {
        Thread listener = new Thread(() -> {
            String stream = "stream:agent:events";
            String group = "group:orchestrator";
            streams.createGroup(stream, group);

            TaskState state = tasks.get(taskId);
            int timeout = 120; // 秒
            int waited = 0;

            while (state != null && !state.isComplete() && waited < timeout) {
                try {
                    // Read all available messages (pending + new)
                    var messages = streams.consumePending(stream, group, "orchestrator:worker-1", 20);
                    for (AgentMessage msg : messages) {
                        if (!taskId.equals(msg.getTaskId())) continue;
                        handleAgentResult(state, msg);
                    }
                    if (messages.isEmpty()) {
                        Thread.sleep(1000);
                        waited++;
                    } else {
                        waited = 0; // reset timeout on activity
                    }
                } catch (Exception e) {
                    log.error("[Orchestrator] listener error: {}", e.getMessage());
                }
            }

            if (state != null && !state.isComplete()) {
                log.warn("[Orchestrator] task {} timed out", taskId);
                sendToFrontend(state.session, Map.of(
                        "type", "error",
                        "taskId", taskId,
                        "message", "任务超时"
                ));
            }
            tasks.remove(taskId);
        }, "orch-listener-" + taskId);
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * 处理智能体返回结果
     */
    private void handleAgentResult(TaskState state, AgentMessage msg) {
        String sender = msg.getSender();
        Performative perf = msg.getPerformative();

        log.info("[Orchestrator] received from {}: {}", sender, perf);

        switch (perf) {
            case PROGRESS -> {
                // 转发进度到前端
                sendToFrontend(state.session, Map.of(
                        "type", "progress",
                        "taskId", state.taskId,
                        "agent", sender,
                        "message", msg.getContent().get("message")
                ));
            }
            case INFORM -> {
                // 记录调用日志
                try {
                    runLogService.recordCall(sender, true, 0);
                } catch (Exception e) {
                    log.warn("[Orchestrator] record call failed: {}", e.getMessage());
                }
                
                // 收集结果
                state.results.put(sender, msg.getContent());

                // 根据发送方执行后续任务
                switch (sender) {
                    case AgentIds.LEARNING_PATH -> {
                        // 路径完成 → 发送到资源生成
                        Object path = msg.getContent().get("path");
                        sendTask(state.taskId, AgentIds.RESOURCE, "batch_generate",
                                Map.of("path", path));
                    }
                    case AgentIds.RESOURCE -> {
                        // 资源完成 → 发送到质量评估
                        Object resources = msg.getContent().get("resources");
                        if (resources == null) {
                            resources = msg.getContent().get("result");
                        }
                        sendTask(state.taskId, AgentIds.QUALITY, "check",
                                Map.of("resources", resources != null ? resources : "none"));
                    }
                    case AgentIds.QUALITY -> {
                        // 质量评估完成 → 全流程结束
                        state.setComplete(true);
                        sendToFrontend(state.session, Map.of(
                                "type", "complete",
                                "taskId", state.taskId,
                                "results", state.results
                        ));
                    }
                    default -> {
                        // 画像等其他结果，暂存
                    }
                }
            }
            case ERROR -> {
                // 记录失败
                try {
                    String errorMsg = (String) msg.getContent().get("error");
                    runLogService.recordCall(sender, false, 0, errorMsg);
                } catch (Exception e) {
                    log.warn("[Orchestrator] record call failed: {}", e.getMessage());
                }
                
                log.error("[Orchestrator] agent {} error: {}", sender, msg.getContent().get("error"));
                sendToFrontend(state.session, Map.of(
                        "type", "agent_error",
                        "taskId", state.taskId,
                        "agent", sender,
                        "error", msg.getContent().get("error")
                ));
            }
        }
    }

    private void sendToFrontend(WebSocketSession session, Map<String, Object> data) {
        try {
            if (session != null && session.isOpen()) {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
                session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            }
        } catch (Exception e) {
            log.error("[Orchestrator] send to frontend failed: {}", e.getMessage());
        }
    }

    /**
     * 任务状态内部类
     */
    private static class TaskState {
        final String taskId;
        final String userId;
        final String goal;
        final WebSocketSession session;
        final Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();
        volatile boolean complete = false;

        TaskState(String taskId, String userId, String goal, WebSocketSession session) {
            this.taskId = taskId;
            this.userId = userId;
            this.goal = goal;
            this.session = session;
        }

        boolean isComplete() { return complete; }
        void setComplete(boolean c) { this.complete = c; }
    }

    /**
     * 聊天消息入口：意图识别 → 路由到对应 Agent
     * 返回 Map: {route, response, agent}
     */
    public Map<String, Object> handleChatMessage(String userId, String message) {
        log.info("[Orchestrator] chat message: userId={}, msg={}", userId, message);
        
        // 用 LLM 做意图识别
        String intent = recognizeIntent(message);
        
        switch (intent) {
            case "greeting":
                return Map.of("route", "direct", "response", 
                    "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？");
            case "help":
                return Map.of("route", "direct", "response",
                    "我可以帮你做这些事情：\n1. 解答问题\n2. 规划学习路径\n3. 生成学习资源（讲义/题目/代码）\n4. 分析学习效果");
            case "learning_path":
                return Map.of("route", "learning_path", "agent", AgentIds.LEARNING_PATH);
            case "resource":
                return Map.of("route", "resource", "agent", AgentIds.RESOURCE);
            case "quiz":
                return Map.of("route", "resource", "agent", AgentIds.RESOURCE);
            case "qa":
            default:
                return Map.of("route", "qa", "agent", AgentIds.QA, "message", message);
        }
    }
    
    /**
     * 用 LLM 识别用户意图
     */
    private String recognizeIntent(String message) {
        try {
            String systemPrompt = "你是一个意图识别系统。根据用户消息，判断用户的主要意图。\n" +
                "只返回以下意图之一（小写英文）：\n" +
                "- greeting: 纯粹的打招呼、问候，没有其他请求（如：你好、hi、hello）\n" +
                "- help: 询问系统功能、帮助（如：你能做什么、有什么功能）\n" +
                "- learning_path: 明确要求生成/规划学习路径、学习计划（如：帮我制定学习计划、生成学习路径）\n" +
                "- resource: 明确要求生成学习资源、讲义、笔记（如：帮我生成讲义、给我一些学习资料）\n" +
                "- quiz: 明确要求做练习题、测试（如：给我出几道题、我想做练习）\n" +
                "- qa: 提问、讨论问题、想了解某个知识点、说想学什么、其他所有情况\n" +
                "重要规则：\n" +
                "1. 如果消息同时包含问候和其他内容（如学习请求、问题），忽略问候，返回主要内容的意图\n" +
                "2. 如果用户说想学什么，返回 qa（不是 learning_path）\n" +
                "3. 只有纯粹的问候（没有任何其他请求）才返回 greeting\n" +
                "只返回意图词，不要其他内容。";
            
            String result = llmClient.chatSimple(systemPrompt, message);
            if (result != null) {
                result = result.trim().toLowerCase().replaceAll("[^a-z_]", "");
            }
            
            // 验证是否是有效意图
            if (result != null && java.util.Set.of("greeting", "help", "learning_path", "resource", "quiz", "qa").contains(result)) {
                log.info("[Orchestrator] intent recognized: {}", result);
                return result;
            }
            
            log.warn("[Orchestrator] invalid intent: {}, defaulting to qa", result);
            return "qa";
        } catch (Exception e) {
            log.error("[Orchestrator] intent recognition failed: {}", e.getMessage());
            return "qa";
        }
    }

}