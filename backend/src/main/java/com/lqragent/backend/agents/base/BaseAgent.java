package com.lqragent.backend.agents.base;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.prompt.service.PromptService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 基类
 * 实现 LLM 推理循环，让智能体真正具备自主决策能力
 * 支持 Agent 间通信（通过 requestPeer 调用其他 Agent）
 */
@Slf4j
public abstract class BaseAgent implements AgentInterface {
    
    protected final String agentId;
    protected final LlmClient llmClient;
    protected final AgentToolRegistry toolRegistry;
    protected final AgentRegistry agentRegistry;
    protected final PromptService promptService;
    protected final ObjectMapper mapper = new ObjectMapper();
    
    /** 当前任务上下文（由 Orchestrator 传入，用于 Agent 间数据共享） */
    protected TaskContext currentContext;
    
    // 最大推理循环次数
    private static final int MAX_ITERATIONS = 5;
    
    protected BaseAgent(String agentId, LlmClient llmClient, AgentToolRegistry toolRegistry, AgentRegistry agentRegistry) {
        this(agentId, llmClient, toolRegistry, agentRegistry, null);
    }
    
    protected BaseAgent(String agentId, LlmClient llmClient, AgentToolRegistry toolRegistry, AgentRegistry agentRegistry, PromptService promptService) {
        this.agentId = agentId;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.agentRegistry = agentRegistry;
        this.promptService = promptService;
    }
    
    /**
     * Spring Bean 初始化后自动注册到 AgentRegistry
     */
    @PostConstruct
    private void autoRegister() {
        agentRegistry.register(this);
        log.info("[{}] auto-registered in AgentRegistry", agentId);
    }
    
    @Override
    public String getAgentId() {
        return agentId;
    }
    
    /**
     * 获取系统提示词（子类实现）
     * 从 prompts/system.md 加载
     */
    protected abstract String getSystemPrompt();
    
    /**
     * 获取可用工具列表（子类实现）
     */
    protected abstract List<AgentTool> getTools();
    
    /**
     * 处理请求（主入口）
     * 实现 LLM 推理循环
     */
    public AgentResponse process(AgentRequest request) {
        return process(request, List.of());
    }
    
    /**
     * 处理请求（主入口，支持对话历史）
     * 实现 LLM 推理循环
     */
    public AgentResponse process(AgentRequest request, List<Map<String, Object>> history) {
        log.info("[{}] processing request: {}", agentId, request);
        
        // 注册工具
        List<AgentTool> tools = getTools();
        for (AgentTool tool : tools) {
            toolRegistry.register(tool);
        }
        
        // 构建用户消息
        String userMessage = buildUserMessage(request);
        
        // LLM 推理循环
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // 添加对话历史（如果有）
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
            log.info("[{}] added {} history messages", agentId, history.size());
        }
        
        // 添加当前用户消息
        messages.add(Map.of("role", "user", "content", userMessage));
        
        List<Map<String, Object>> toolSchemas = toolRegistry.getToolSchemas();
        String systemPrompt = getSystemPrompt();
        
        List<ToolExecution> executions = new ArrayList<>();
        
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            log.info("[{}] iteration {}/{}", agentId, iteration + 1, MAX_ITERATIONS);
            
            // 调用 LLM
            LlmClient.LlmResponse llmResponse = llmClient.chat(
                    systemPrompt, messages, toolSchemas
            );
            
            if (!llmResponse.isSuccess()) {
                log.error("[{}] LLM call failed: {}", agentId, llmResponse.error());
                return AgentResponse.failure("LLM call failed: " + llmResponse.error());
            }
            
            // 如果没有 tool_calls，说明 LLM 给出了最终答案
            if (!llmResponse.hasToolCalls()) {
                String finalAnswer = llmResponse.content();
                log.info("[{}] completed after {} iterations", agentId, iteration + 1);
                return AgentResponse.success(finalAnswer, executions);
            }
            
            // 有 tool_calls，执行工具
            List<LlmClient.ToolCall> toolCalls = llmResponse.toolCalls();
            log.info("[{}] executing {} tool calls", agentId, toolCalls.size());
            
            // 将 assistant 消息（含 tool_calls）加入历史
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", llmResponse.content() != null ? llmResponse.content() : "");
            assistantMsg.put("tool_calls", toolCalls.stream()
                    .map(tc -> Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())
                    ))
                    .toList());
            messages.add(assistantMsg);
            
            // 执行每个 tool_call
            for (LlmClient.ToolCall tc : toolCalls) {
                log.info("[{}] executing tool: {}", agentId, tc.name());
                
                Map<String, Object> args = tc.parseArguments();
                AgentTool.ToolResult result = toolRegistry.execute(tc.name(), args);
                
                executions.add(new ToolExecution(tc.name(), args, result));
                
                // 将 tool 结果加入消息历史
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", (Object) tc.id(),
                        "content", (Object) (result.success() ? result.content() : "Error: " + result.content())
                ));
                
                log.info("[{}] tool {} result: {}", agentId, tc.name(), 
                        result.success() ? "success" : "failed");
            }
        }
        
        // 达到最大迭代次数
        log.warn("[{}] reached max iterations", agentId);
        return AgentResponse.failure("Reached maximum iterations");
    }
    
    /**
     * 构建用户消息（子类可覆盖）
     */
    protected String buildUserMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务: ").append(request.action()).append("\n");
        if (request.goal() != null) {
            sb.append("目标: ").append(request.goal()).append("\n");
        }
        if (request.context() != null) {
            sb.append("上下文: ").append(mapper.valueToTree(request.context()).toString());
        }
        return sb.toString();
    }
    
    /**
     * 从 PromptService 获取提示词（支持动态管理）
     * 如果 PromptService 不可用，则从 classpath 文件加载
     */
    protected String getManagedPrompt() {
        if (promptService != null) {
            return promptService.getPrompt(agentId);
        }
        return "You are a helpful assistant.";
    }
    
    /**
     * 从 classpath 加载提示词文件
     */
    protected String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[{}] failed to load prompt: {}", agentId, path, e);
            return "You are a helpful assistant.";
        }
    }
    
    /**
     * 处理请求（带 TaskContext，支持 Agent 间通信）
     * Agent 内部可以通过 TaskContext 共享数据，或通过 requestPeer() 调用其他 Agent
     */
    @Override
    public AgentResponse process(AgentRequest request, TaskContext context) {
        this.currentContext = context;
        try {
            return process(request, List.of());
        } finally {
            this.currentContext = null;
        }
    }
    
    /**
     * 调用其他 Agent
     * 在 Agent 执行过程中，可以调用其他 Agent 获取数据或执行任务
     * 
     * @param peerAgentId 目标 Agent ID
     * @param params 参数（至少包含 goal 字段）
     * @return 目标 Agent 的响应
     */
    protected AgentResponse requestPeer(String peerAgentId, Map<String, Object> params) {
        if (agentRegistry == null) {
            log.warn("[{}] AgentRegistry not injected, cannot call peer: {}", agentId, peerAgentId);
            return AgentResponse.failure("AgentRegistry not available");
        }
        
        Optional<AgentInterface> peerOpt = agentRegistry.getAgent(peerAgentId);
        if (peerOpt.isEmpty()) {
            log.warn("[{}] Peer agent not found: {}", agentId, peerAgentId);
            return AgentResponse.failure("Agent not found: " + peerAgentId);
        }
        
        AgentInterface peer = peerOpt.get();
        String goal = params.getOrDefault("goal", "").toString();
        AgentRequest peerRequest = new AgentRequest("process", goal, params);
        
        log.info("[{}] requesting peer: {} with params={}", agentId, peerAgentId, params);
        long start = System.currentTimeMillis();
        
        try {
            AgentResponse response;
            if (currentContext != null) {
                // 如果有上下文，传递给目标 Agent
                response = peer.process(peerRequest, currentContext);
            } else {
                response = peer.process(peerRequest);
            }
            
            long duration = System.currentTimeMillis() - start;
            log.info("[{}] peer {} responded in {}ms, success={}", 
                    agentId, peerAgentId, duration, response.success());
            
            return response;
        } catch (Exception e) {
            log.error("[{}] peer {} failed: {}", agentId, peerAgentId, e.getMessage(), e);
            return AgentResponse.failure("Peer call failed: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前 TaskContext
     */
    protected TaskContext getCurrentContext() {
        return currentContext;
    }
    
    /**
     * Agent 请求
     */
    public record AgentRequest(
            String action,
            String goal,
            Map<String, Object> context
    ) {}
    
    /**
     * Agent 响应
     */
    public record AgentResponse(
            boolean success,
            String content,
            List<ToolExecution> executions,
            String error
    ) {
        public static AgentResponse success(String content, List<ToolExecution> executions) {
            return new AgentResponse(true, content, executions, null);
        }
        
        public static AgentResponse failure(String error) {
            return new AgentResponse(false, null, List.of(), error);
        }
    }
    
    /**
     * 工具执行记录
     */
    public record ToolExecution(
            String toolName,
            Map<String, Object> args,
            AgentTool.ToolResult result
    ) {}
}
