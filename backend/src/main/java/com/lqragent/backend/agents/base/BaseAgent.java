package com.lqragent.backend.agents.base;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 基类
 * 实现 LLM 推理循环，让智能体真正具备自主决策能力
 */
@Slf4j
public abstract class BaseAgent {
    
    protected final String agentId;
    protected final LlmClient llmClient;
    protected final AgentToolRegistry toolRegistry;
    protected final ObjectMapper mapper = new ObjectMapper();
    
    // 最大推理循环次数
    private static final int MAX_ITERATIONS = 5;
    
    protected BaseAgent(String agentId, LlmClient llmClient, AgentToolRegistry toolRegistry) {
        this.agentId = agentId;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
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
