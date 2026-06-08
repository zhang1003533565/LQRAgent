package com.lqragent.backend.agents.serve.qa.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "search_knowledge"; }
    
    @Override
    public String description() { return "从知识库中检索相关信息，用于回答用户问题"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索查询"),
                        "topK", Map.of("type", "integer", "description", "返回结果数量")
                ),
                "required", new String[]{"query"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String query = args.get("query").toString();
            int topK = args.containsKey("topK") ? Integer.parseInt(args.get("topK").toString()) : 3;
            
            // 调用 ai-server 知识库检索
            // 使用 streamChat 方法进行 RAG 检索
            StringBuilder resultContent = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
            
            // 获取知识库配置
            String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
            
            // 使用 streamChat 进行 RAG 检索
            aiServerWsProxy.streamChat(
                null, // sessionId
                query, // userMessage
                java.util.List.of(publicKb), // knowledgeBases
                new AiServerWsProxy.StreamCallback() {
                    @Override
                    public void onChunk(String content) {
                        resultContent.append(content);
                    }
                    
                    @Override
                    public void onDone(String aiServerSessionId) {
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(String error) {
                        errorRef.set(error);
                        latch.countDown();
                    }
                }
            );
            
            // 等待结果（最多 30 秒）
            boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!completed) {
                return ToolResult.failure("知识检索超时");
            }
            
            if (errorRef.get() != null) {
                return ToolResult.failure("知识检索失败: " + errorRef.get());
            }
            
            String content = resultContent.toString().trim();
            if (content.isEmpty()) {
                // 如果没有检索到内容，返回默认提示
                content = "未找到与 \"" + query + "\" 相关的知识库内容。请尝试更具体的查询。";
            }
            
            // 构建结果
            java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
            results.add(Map.of(
                    "content", content,
                    "source", "knowledge_base",
                    "score", 1.0
            ));
            
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "query", query,
                    "results", results,
                    "totalFound", results.size(),
                    "summary", "找到 " + results.size() + " 条相关知识"
            )));
        } catch (Exception e) {
            return ToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }
}
