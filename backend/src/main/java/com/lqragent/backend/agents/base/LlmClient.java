package com.lqragent.backend.agents.base;

import org.apache.hc.core5.util.TimeValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM 客户端
 * 通过 DeepSeek API 调用 LLM，支持 tool_calls
 */
@Slf4j
@Component
public class LlmClient {
    
    private final AppRuntimeConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    
    public LlmClient(AppRuntimeConfig config) {
        this.config = config;
    }
    
    /**
     * 调用 LLM（带工具）
     */
    public LlmResponse chat(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        String apiKey = config.get("llm.api-key", "");
        String baseUrl = config.get("llm.host", "https://api.deepseek.com");
        String model = config.get("llm.model", "deepseek-chat");
        
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // 构建消息列表（每条消息加上 type 字段，兼容 DeepSeek API schema）
        List<Map<String, Object>> allMessages = new ArrayList<>();
        // 系统消息
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", "system");
            m.put("type", "system");
            m.put("content", systemPrompt);
            allMessages.add(m);
        }
        // 业务消息（来自调用方）
        for (Map<String, Object> msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>(msg);
            if (!m.containsKey("type") && m.containsKey("role")) {
                m.put("type", m.get("role"));
            }
            allMessages.add(m);
        }
        
        // 构建请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", allMessages);
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.1);
        
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }
        
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            log.info("[LlmClient] calling LLM: host={}, model={}, apiKey={}...", baseUrl, model, 
                    apiKey.length() > 8 ? apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4) : "(short)");
            
            // 连接池：最多 20 个连接，每条路由最多 10 个
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(20);
            connManager.setDefaultMaxPerRoute(10);
            connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setSocketTimeout(Timeout.ofSeconds(30))
                    .build());

            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory(
                            HttpClients.custom()
                                    .setConnectionManager(connManager)
                                    .evictExpiredConnections()
                                    .evictIdleConnections(TimeValue.ofSeconds(30))
                                    .build());

            String response = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build()
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);
            
            log.info("[LlmClient] response received, length={}", response != null ? response.length() : 0);
            return parseResponse(response);
            
        } catch (Exception e) {
            log.error("[LlmClient] call failed: {}", e.getMessage(), e);
            // 记录部分请求体用于调试
            String preview = requestBody.toString();
            if (preview.length() > 500) preview = preview.substring(0, 500) + "...";
            log.error("[LlmClient] request body preview: {}", preview);
            return new LlmResponse(null, null, "LLM call failed: " + e.getMessage());
        }
    }
    
    /**
     * 简单调用（无工具）
     */
    public String chatSimple(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", userMessage)
        );
        LlmResponse response = chat(systemPrompt, messages, null);
        return response.content();
    }
    
    private LlmResponse parseResponse(String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                return new LlmResponse(null, null, "No choices in response");
            }
            
            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText(null);
            
            // 解析 tool_calls
            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    String argsJson = tc.path("function").path("arguments").asText("{}");
                    toolCalls.add(new ToolCall(id, name, argsJson));
                }
            }
            
            return new LlmResponse(content, toolCalls, null);
            
        } catch (Exception e) {
            log.error("[LlmClient] parse failed: {}", e.getMessage());
            return new LlmResponse(null, null, "Parse failed: " + e.getMessage());
        }
    }
    
    /**
     * LLM 响应
     */
    public record LlmResponse(
            String content,
            List<ToolCall> toolCalls,
            String error
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
        
        public boolean isSuccess() {
            return error == null;
        }
    }
    
    /**
     * 工具调用
     */
    public record ToolCall(
            String id,
            String name,
            String argumentsJson
    ) {
        public Map<String, Object> parseArguments() {
            try {
                return new ObjectMapper().readValue(argumentsJson, Map.class);
            } catch (Exception e) {
                return Map.of();
            }
        }
    }
}
