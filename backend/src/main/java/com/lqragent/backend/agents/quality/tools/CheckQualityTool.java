package com.lqragent.backend.agents.quality.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckQualityTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "check_quality"; }
    
    @Override
    public String description() { return "调用 ai-server 检查资源质量"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string", "description", "资源标题"),
                        "content", Map.of("type", "string", "description", "待检查的内容")
                ),
                "required", new String[]{"content"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String title = args.get("title") != null ? args.get("title").toString() : "";
            String content = args.get("content").toString();
            
            // 调用 ai-server 质量检查
            String result = aiServerWsProxy.qualityCheck(title, content);
            
            if (result != null && !result.isBlank()) {
                Map<String, Object> data = Map.of(
                        "passed", true,
                        "checkResult", result,
                        "source", "ai-server",
                        "summary", "质量检查完成"
                );
                return ToolResult.success(mapper.writeValueAsString(data));
            }
            
            // 降级：基础检查
            StringBuilder failures = new StringBuilder();
            if (content.isBlank()) failures.append("- 内容为空\n");
            if (content.length() < 10) failures.append("- 内容过短\n");
            
            boolean passed = failures.length() == 0;
            Map<String, Object> data = Map.of(
                    "passed", passed,
                    "failures", failures.toString(),
                    "source", "fallback",
                    "summary", passed ? "基础检查通过" : "检查未通过: " + failures
            );
            return ToolResult.success(mapper.writeValueAsString(data));
        } catch (Exception e) {
            return ToolResult.failure("质量检查失败: " + e.getMessage());
        }
    }
}
