package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentInterface;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.prompt.service.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 智能体基类（v3 Streams 版）
 * <p>
 * 所有业务智能体继承此类，实现 getSystemPrompt() / getTools() / process() 即可。
 * <p>
 * 通信方式：Agent 监听 stream:agent:{agentId}，收到 REQUEST 后运行 LLM 循环，
 * 结果通过 stream:agent:events 返回。Agent 间无直接调用，全部经协调体中转。
 * <p>
 * 与旧框架 agents.base.BaseAgent 的区别：
 * - 不实现 AgentInterface（不走同步方法调用）
 * - LLM 循环作为工具方法由子类自行调用
 * - 全部通信走 Redis Streams
 */
@Slf4j
public abstract class BaseAgent implements AgentInterface {

    // ==================== Redis Streams ====================
    protected final String agentId;
    protected final RedisStreamsService streams;
    private volatile boolean running = false;
    private Thread consumerThread;

    // ==================== LLM 推理 ====================
    protected final LlmClient llmClient;
    protected final AgentToolRegistry toolRegistry;
    protected final PromptService promptService;
    protected final ObjectMapper mapper = new ObjectMapper();

    protected static final int MAX_ITERATIONS = 3;

    /** CapabilityRegistry（CFP 协商协议使用） */
    protected CapabilityRegistry capabilityRegistry;

    /** AgentRegistry（PipelineEngine 使用） */
    @Autowired(required = false)
    protected AgentRegistry agentRegistry;

    /** 阶段一新增：AgentCardRegistry（声明式能力目录，供 PlanningAgent v2 使用） */
    @Autowired(required = false)
    protected AgentCardRegistry agentCardRegistry;

    protected BaseAgent(String agentId, RedisStreamsService streams,
                        LlmClient llmClient, AgentToolRegistry toolRegistry,
                        PromptService promptService) {
        this.agentId = agentId;
        this.streams = streams;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.promptService = promptService;
    }

    public void setCapabilityRegistry(CapabilityRegistry registry) {
        this.capabilityRegistry = registry;
    }

    public void setAgentRegistry(AgentRegistry registry) {
        this.agentRegistry = registry;
    }

    // ==================== 子类必须实现 ====================

    /** 获取系统提示词 */
    protected abstract String getSystemPrompt();

    /** 获取可用工具列表 */
    protected abstract List<AgentTool> getTools();

    /**
     * 阶段一新增：声明 Agent 能力卡（强烈建议子类覆盖）
     * 启动时自动注册到 AgentCardRegistry
     * <p>
     * 默认实现返回兜底卡片，子类应根据真实能力覆盖
     */
    public AgentCard getAgentCard() {
        return AgentCard.simple(
                agentId,
                agentId,
                "Agent " + agentId,
                List.of(),
                List.of("text")
        );
    }

    /**
     * 处理消息（子类实现）
     * 通常调用 executeLlmLoop(msg) 或 executeLlmLoop(msg, history) 来完成 LLM 推理
     */
    public abstract AgentMessage process(AgentMessage request);

    // ==================== AgentInterface 适配器方法 ====================

    @Override
    public String getAgentId() {
        return agentId;
    }

    /**
     * 适配 AgentInterface：将 AgentRequest 转换为 AgentMessage 并调用 process
     */
    @Override
    public AgentResponse process(AgentRequest request) {
        return process(request, (TaskContext) null);
    }

    @Override
    public AgentResponse process(AgentRequest request,
            List<Map<String, Object>> history) {
        return process(request, (TaskContext) null);
    }

    @Override
    public AgentResponse process(AgentRequest request, TaskContext context) {
        try {
            // 将 AgentRequest 转换为 AgentMessage
            AgentMessage msg = AgentMessage.request(
                    "pipeline-" + System.currentTimeMillis(),
                    "pipeline",
                    agentId,
                    request.context() != null ? request.context() : Map.of("goal", request.goal())
            );
            AgentMessage result = process(msg);
            // 将 AgentMessage 转换为 AgentResponse
            boolean success = result.getPerformative() == Performative.INFORM;
            String content = success ? String.valueOf(result.getContent().getOrDefault("content", "")) : null;
            String error = success ? null : String.valueOf(result.getContent().getOrDefault("error", "Unknown error"));
            // 传递 metadata（如 ragSources）
            Map<String, Object> metadata = new LinkedHashMap<>();
            Object ragSources = result.getContent().get("ragSources");
            if (ragSources != null) {
                metadata.put("ragSources", ragSources);
            }
            Object artifactKind = result.getContent().get("artifactKind");
            Object artifactPayload = result.getContent().get("artifactPayload");
            if (artifactKind != null && artifactPayload != null) {
                metadata.put("artifactKind", artifactKind);
                metadata.put("artifactPayload", artifactPayload);
            }
            return AgentResponse.success(content, List.of(), metadata);
        } catch (Exception e) {
            log.error("[{}] adapter process failed: {}", agentId, e.getMessage());
            return AgentResponse.failure(e.getMessage());
        }
    }

    // ==================== Redis Streams 消费者 ====================

    @PostConstruct
    public void start() {
        // 注册到 AgentRegistry（供 PipelineEngine 使用）
        if (agentRegistry != null) {
            agentRegistry.register(this);
            log.info("[{}] registered in AgentRegistry", agentId);
        }

        // 阶段一新增：自动注册 AgentCard 到 AgentCardRegistry
        if (agentCardRegistry != null) {
            try {
                agentCardRegistry.register(getAgentCard());
            } catch (Exception e) {
                log.warn("[{}] failed to register AgentCard: {}", agentId, e.getMessage());
            }
        }

        String stream = "stream:agent:" + agentId;
        String group = "group:" + agentId;
        streams.createGroup(stream, group);

        running = true;
        consumerThread = new Thread(() -> {
            log.info("[{}] started, consuming from {}", agentId, stream);
            while (running) {
                try {
                    var messages = streams.consume(stream, group, agentId + ":worker-1", 1);
                    for (AgentMessage msg : messages) {
                        if (msg.getPerformative() == Performative.REQUEST) {
                            try {
                                AgentMessage result = process(msg);
                                streams.send("stream:agent:events", result);
                            } catch (Exception e) {
                                log.error("[{}] process failed: {}", agentId, e.getMessage(), e);
                                streams.send("stream:agent:events",
                                        AgentMessage.error(msg.getTaskId(), agentId, e.getMessage()));
                            }
                        } else if (msg.getPerformative() == Performative.CFP) {
                            handleCfp(msg);
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("[{}] consume error: {}", agentId, e.getMessage());
                    }
                }
            }
            log.info("[{}] stopped", agentId);
        }, "agent-" + agentId);
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    // ==================== LLM 推理循环 ====================

    /**
     * 运行 LLM 推理循环
     * 子类在 process() 中调用此方法
     *
     * @param request  收到的消息
     * @return 回复消息（INFORM 或 ERROR）
     */
    protected AgentMessage executeLlmLoop(AgentMessage request) {
        return executeLlmLoop(request, null);
    }

    /**
     * 运行 LLM 推理循环（支持对话历史）
     *
     * @param request  收到的消息
     * @param history  对话历史（可选）
     * @return 回复消息（INFORM 或 ERROR）
     */
    protected AgentMessage executeLlmLoop(AgentMessage request,
                                           List<Map<String, Object>> history) {
        String taskId = request.getTaskId();
        log.info("[{}] LLM loop: taskId={}", agentId, taskId);

        // 注册工具
        List<AgentTool> tools = getTools();
        for (AgentTool tool : tools) {
            toolRegistry.register(tool);
        }

        // 构建消息
        String userMessage = buildUserMessage(request);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> toolSchemas = toolRegistry.getToolSchemas();
        String systemPrompt = getSystemPrompt();

        // 收集工具执行过程中产生的 RAG 引用来源和多模态产物
        List<Map<String, Object>> collectedRagSources = new ArrayList<>();
        String artifactKind = null;
        Object artifactPayload = null;

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            log.debug("[{}] LLM iteration {}/{}", agentId, iteration + 1, MAX_ITERATIONS);

            LlmClient.LlmResponse llmResponse = llmClient.chat(
                    systemPrompt, messages, toolSchemas);

            if (!llmResponse.isSuccess()) {
                return AgentMessage.error(taskId, agentId,
                        "LLM call failed: " + llmResponse.error());
            }

            if (!llmResponse.hasToolCalls()) {
                Map<String, Object> resultContent = new LinkedHashMap<>();
                resultContent.put("content", llmResponse.content());
                resultContent.put("status", "completed");
                if (!collectedRagSources.isEmpty()) {
                    resultContent.put("ragSources", collectedRagSources);
                }
                if (artifactKind != null && artifactPayload != null) {
                    resultContent.put("artifactKind", artifactKind);
                    resultContent.put("artifactPayload", artifactPayload);
                }
                log.info("[{}] LLM completed after {} iterations", agentId, iteration + 1);
                return AgentMessage.inform(taskId, agentId, "pipeline", resultContent);
            }

            // 执行工具调用
            List<LlmClient.ToolCall> toolCalls = llmResponse.toolCalls();
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", llmResponse.content() != null ? llmResponse.content() : "");
            assistantMsg.put("tool_calls", toolCalls.stream()
                    .map(tc -> Map.of("id", tc.id(), "type", "function",
                            "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())))
                    .toList());
            messages.add(assistantMsg);

            for (LlmClient.ToolCall tc : toolCalls) {
                log.info("[{}] tool: {}", agentId, tc.name());
                Map<String, Object> args = tc.parseArguments();
                try {
                    AgentTool.ToolResult result = toolRegistry.execute(tc.name(), args);
                    messages.add(Map.of("role", "tool", "tool_call_id", (Object) tc.id(),
                            "content", (Object) (result.success() ? result.content() : "Error: " + result.content())));
                    // 收集 RAG 引用来源
                    if (result.success() && result.metadata() != null) {
                        Object ragSources = result.metadata().get("ragSources");
                        if (ragSources instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Map<?, ?> map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> source = (Map<String, Object>) map;
                                    collectedRagSources.add(source);
                                }
                            }
                        }
                    }
                    if (result.success()) {
                        ArtifactInfo artifact = detectToolArtifact(result.content());
                        if (artifact != null) {
                            artifactKind = artifact.kind();
                            artifactPayload = artifact.payload();
                        }
                    }
                } catch (Exception e) {
                    log.error("[{}] tool {} error: {}", agentId, tc.name(), e.getMessage());
                    messages.add(Map.of("role", "tool", "tool_call_id", (Object) tc.id(),
                            "content", (Object) ("Error: " + e.getMessage())));
                }
            }

            sendProgress(taskId, "iteration " + (iteration + 1));
        }

        return AgentMessage.error(taskId, agentId, "Reached maximum iterations");
    }

    private record ArtifactInfo(String kind, Object payload) {}

    private ArtifactInfo detectToolArtifact(String content) {
        if (content == null || content.isBlank()) return null;
        try {
            var root = mapper.readTree(content);
            if (root.has("type") && "quiz".equals(root.path("type").asText()) && root.has("data")) {
                return new ArtifactInfo("quiz", mapper.convertValue(root.path("data"), Object.class));
            }
            if (root.has("questions")) {
                return new ArtifactInfo("quiz", mapper.convertValue(root, Object.class));
            }
            String mediaUrl = root.path("mediaUrl").asText("");
            if (mediaUrl.isBlank()) mediaUrl = root.path("imageUrl").asText("");
            if (mediaUrl.isBlank()) mediaUrl = root.path("videoUrl").asText("");
            if (mediaUrl.isBlank()) mediaUrl = root.path("url").asText("");
            if (!mediaUrl.isBlank()) {
                String mediaType = root.path("mediaType").asText("");
                if (mediaType.isBlank() && (mediaUrl.endsWith(".mp4") || mediaUrl.endsWith(".webm") || mediaUrl.endsWith(".mov"))) {
                    mediaType = "video";
                }
                var payload = new LinkedHashMap<String, Object>();
                payload.put("url", mediaUrl);
                payload.put("prompt", root.path("prompt").asText(""));
                payload.put("mediaType", mediaType.isBlank() ? "image" : mediaType);
                return new ArtifactInfo("video".equalsIgnoreCase(mediaType) ? "video" : "media_image", payload);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 构建用户消息（子类可覆盖）
     */
    protected String buildUserMessage(AgentMessage request) {
        Map<String, Object> content = request.getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("任务: ").append(content.getOrDefault("action", "process")).append("\n");
        if (content.containsKey("goal")) {
            sb.append("目标: ").append(content.get("goal")).append("\n");
        }
        sb.append("用户ID: ").append(content.getOrDefault("userId", "unknown")).append("\n");
        Map<String, Object> context = new LinkedHashMap<>(content);
        context.remove("action");
        context.remove("goal");
        context.remove("userId");
        context.remove("sessionId");
        if (!context.isEmpty()) {
            sb.append("上下文: ").append(mapper.valueToTree(context));
        }
        return sb.toString();
    }

    // ==================== Prompt 管理 ====================

    protected String getManagedPrompt() {
        if (promptService != null) {
            return promptService.getPrompt(agentId);
        }
        return "You are a helpful assistant.";
    }

    protected String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[{}] failed to load prompt: {}", agentId, path, e);
            return "You are a helpful assistant.";
        }
    }

    // ==================== 进度通知 ====================

    protected void sendProgress(String taskId, String message) {
        streams.send("stream:agent:events", AgentMessage.progress(taskId, agentId, message));
    }

    // ==================== CFP 协商协议 ====================

    protected void handleCfp(AgentMessage cfp) {
        try {
            String description = (String) cfp.getContent().getOrDefault("description", "");
            boolean canHandle = evaluateCapability(description);
            if (canHandle) {
                Map<String, Object> proposal = Map.of(
                        "agentId", agentId,
                        "confidence", 0.8,
                        "estimatedDuration", estimateDuration(description),
                        "description", "I can handle this task");
                streams.send("stream:agent:events",
                        AgentMessage.builder().id(UUID.randomUUID().toString())
                                .taskId(cfp.getTaskId()).performative(Performative.PROPOSE)
                                .sender(agentId).receiver(cfp.getSender()).content(proposal)
                                .timestamp(System.currentTimeMillis()).build());
            } else {
                streams.send("stream:agent:events",
                        AgentMessage.builder().id(UUID.randomUUID().toString())
                                .taskId(cfp.getTaskId()).performative(Performative.REFUSE)
                                .sender(agentId).receiver(cfp.getSender())
                                .content(Map.of("reason", "Cannot handle this task"))
                                .timestamp(System.currentTimeMillis()).build());
            }
        } catch (Exception e) {
            log.error("[{}] handleCfp failed: {}", agentId, e.getMessage());
        }
    }

    protected boolean evaluateCapability(String taskDescription) {
        if (capabilityRegistry == null) return true;
        return capabilityRegistry.findById(agentId)
                .map(cap -> cap.tags().stream().anyMatch(taskDescription.toLowerCase()::contains)
                        || cap.description().toLowerCase().contains(taskDescription.toLowerCase()))
                .orElse(false);
    }

    protected long estimateDuration(String taskDescription) {
        if (capabilityRegistry == null) return 30000;
        return capabilityRegistry.findById(agentId)
                .map(AgentCapability::avgLatencyMs)
                .orElse(30000L);
    }

    protected List<AgentCapability> findCapablePeers(String keyword) {
        if (capabilityRegistry == null) return List.of();
        return capabilityRegistry.findByKeyword(keyword);
    }

    protected Optional<AgentCapability> getPeerCapability(String peerAgentId) {
        if (capabilityRegistry == null) return Optional.empty();
        return capabilityRegistry.findById(peerAgentId);
    }
}
