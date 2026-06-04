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
public class GenerateQuizTool implements AgentTool {
    
    private final ResourceGenerationService resourceService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_quiz"; }
    
    @Override
    public String description() { return "为指定知识点生成练习题（选择题+填空题+编程题）"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "kpId", Map.of("type", "string", "description", "知识点ID")
                ),
                "required", new String[]{"kpId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String kpId = args.get("kpId").toString();
            ResourceGenerateRequest request = ResourceGenerateRequest.builder()
                    .kpId(kpId)
                    .resourceType("QUIZ")
                    .build();
            var result = resourceService.generate(request);
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("生成练习题失败: " + e.getMessage());
        }
    }
}
