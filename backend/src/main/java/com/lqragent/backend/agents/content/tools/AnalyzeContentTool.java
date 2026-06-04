package com.lqragent.backend.agents.content.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.contentanalyzer.service.ContentAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyzeContentTool implements AgentTool {
    
    private final ContentAnalyzerService contentService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "analyze_content"; }
    
    @Override
    public String description() { return "分析文档内容，提取关键信息和知识点"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "kbName", Map.of("type", "string", "description", "知识库名称"),
                        "fileName", Map.of("type", "string", "description", "文件名")
                ),
                "required", new String[]{"kbName", "fileName"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String kbName = args.get("kbName").toString();
            String fileName = args.get("fileName").toString();
            var result = contentService.analyze(kbName, fileName);
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("分析失败: " + e.getMessage());
        }
    }
}
