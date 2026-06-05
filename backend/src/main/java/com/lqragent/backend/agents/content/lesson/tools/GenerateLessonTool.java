package com.lqragent.backend.agents.content.lesson.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateLessonTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_lesson"; }
    
    @Override
    public String description() { return "根据知识点生成讲义内容"; }
    
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
            
            // 生成讲义内容
            String content = "# " + title + "\n\n" +
                "## 概述\n" +
                "本讲义介绍 " + kpId + " 的核心概念。\n\n" +
                "## 核心知识点\n" +
                "1. 基本概念\n" +
                "2. 使用方法\n" +
                "3. 最佳实践\n\n" +
                "## 示例\n" +
                "```python\n" +
                "# 示例代码\n" +
                "print(\"Hello, World!\")\n" +
                "```\n";
            
            Map<String, Object> result = Map.of(
                    "kpId", kpId,
                    "title", title,
                    "content", content,
                    "format", "markdown"
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("生成讲义失败: " + e.getMessage());
        }
    }
}
