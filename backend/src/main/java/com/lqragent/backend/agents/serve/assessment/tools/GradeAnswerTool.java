package com.lqragent.backend.agents.serve.assessment.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GradeAnswerTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "grade_answer"; }
    
    @Override
    public String description() { return "# 评估批改智能体"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "integer", "description", "用户ID")
                ),
                "required", new String[]{"userId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Long userId = Long.parseLong(args.get("userId").toString());
            
            // 评估答案
            String answer = args.get("answer") != null ? args.get("answer").toString() : "";
            int score = answer.length() > 10 ? 80 : 50;
            String feedback = score >= 70 ? "回答不错！" : "需要加强理解。";
            
            Map<String, Object> result = Map.of(
                    "score", score,
                    "feedback", feedback,
                    "passed", score >= 60,
                    "summary", "得分: " + score + " 分"
            );

            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("assessment 执行失败: " + e.getMessage());
        }
    }
}
