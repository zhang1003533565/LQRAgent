package com.lqragent.backend.agents.lesson.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateLessonTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_lesson"; }
    
    @Override
    public String description() { return "调用 ai-server 生成讲义内容"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "kpId", Map.of("type", "string", "description", "知识点ID"),
                        "title", Map.of("type", "string", "description", "讲义标题")
                ),
                "required", new String[]{"kpId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String kpId = args.get("kpId") != null ? args.get("kpId").toString() : "";
            String title = args.get("title") != null ? args.get("title").toString() : "学习讲义";
            
            // 调用 ai-server 生成资源
            String result = aiServerWsProxy.generateResource("LESSON", title, kpId);
            
            if (result != null && !result.isBlank()) {
                Map<String, Object> data = Map.of(
                        "kpId", kpId,
                        "title", title,
                        "content", result,
                        "format", "markdown",
                        "source", "ai-server"
                );
                return ToolResult.success(mapper.writeValueAsString(data));
            }
            
            // 降级：返回基本内容
            String content = "# " + title + "\n\n" +
                "## 概述\n" +
                "本讲义介绍 " + kpId + " 的核心概念。\n\n" +
                "## 核心知识点\n" +
                "1. 基本概念\n" +
                "2. 使用方法\n" +
                "3. 最佳实践\n";
            
            Map<String, Object> data = Map.of(
                    "kpId", kpId,
                    "title", title,
                    "content", content,
                    "format", "markdown",
                    "source", "fallback"
            );
            return ToolResult.success(mapper.writeValueAsString(data));
        } catch (Exception e) {
            return ToolResult.failure("生成讲义失败: " + e.getMessage());
        }
    }
}
