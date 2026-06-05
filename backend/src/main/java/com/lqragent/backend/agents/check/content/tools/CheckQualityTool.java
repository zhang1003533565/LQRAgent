package com.lqragent.backend.agents.check.content.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckQualityTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "check_quality"; }
    
    @Override
    public String description() { return "检查资源质量，包括非空检查、长度检查、格式检查"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "content", Map.of("type", "string", "description", "待检查的内容"),
                        "title", Map.of("type", "string", "description", "资源标题")
                ),
                "required", new String[]{"content"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String content = args.get("content") != null ? args.get("content").toString() : "";
            String title = args.get("title") != null ? args.get("title").toString() : "";
            
            StringBuilder failures = new StringBuilder();
            
            // 1. 非空检查
            if (content.isBlank()) {
                failures.append("- 内容为空\n");
            }
            if (title.isBlank()) {
                failures.append("- 标题为空\n");
            }
            
            // 2. 长度检查
            if (content.length() < 10) {
                failures.append("- 内容过短(至少10字符)\n");
            }
            if (content.length() > 10000) {
                failures.append("- 内容过长(最多10000字符)\n");
            }
            
            // 3. 格式检查
            if (content.contains("<script>")) {
                failures.append("- 包含不安全脚本标签\n");
            }
            
            boolean passed = failures.length() == 0;
            String summary = passed ? "所有检查通过" : "未通过:\n" + failures;
            
            Map<String, Object> result = Map.of(
                    "passed", passed,
                    "failures", failures.toString(),
                    "summary", summary,
                    "contentLength", content.length()
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("质量检查失败: " + e.getMessage());
        }
    }
}
