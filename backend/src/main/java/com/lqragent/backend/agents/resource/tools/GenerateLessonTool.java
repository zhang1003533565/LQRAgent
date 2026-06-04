package com.lqragent.backend.agents.resource.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateLessonTool implements AgentTool {
    
    private final ResourceGenerationService resourceService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_lesson"; }
    
    @Override
    public String description() { return "为指定知识点生成讲义（Markdown格式）"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "kpId", Map.of("type", "string", "description", "知识点ID"),
                        "customPrompt", Map.of("type", "string", "description", "自定义提示词（可选）")
                ),
                "required", new String[]{"kpId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String kpId = args.get("kpId").toString();
            String customPrompt = args.containsKey("customPrompt") ? args.get("customPrompt").toString() : null;
            
            ResourceGenerateRequest request = ResourceGenerateRequest.builder()
                    .kpId(kpId)
                    .resourceType("LESSON")
                    .customPrompt(customPrompt)
                    .build();
            
            var result = resourceService.generate(request);
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("生成讲义失败: " + e.getMessage());
        }
    }
}
