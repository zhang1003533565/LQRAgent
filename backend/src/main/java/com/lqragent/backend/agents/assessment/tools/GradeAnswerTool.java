package com.lqragent.backend.agents.assessment.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GradeAnswerTool implements AgentTool {
    
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "grade_answer"; }
    
    @Override
    public String description() { return "调用 LLM 评估答案质量并给出反馈"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "question", Map.of("type", "string", "description", "题目"),
                        "answer", Map.of("type", "string", "description", "用户答案"),
                        "expectedAnswer", Map.of("type", "string", "description", "标准答案（可选）")
                ),
                "required", new String[]{"answer"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String question = args.get("question") != null ? args.get("question").toString() : "";
            String answer = args.get("answer").toString();
            String expectedAnswer = args.get("expectedAnswer") != null ? args.get("expectedAnswer").toString() : "";
            
            // 调用 LLM 评估
            String prompt = String.format(
                "题目: %s\n用户答案: %s\n标准答案: %s\n\n请评估答案质量，给出0-100分和简短评语。",
                question.isEmpty() ? "未提供" : question,
                answer,
                expectedAnswer.isEmpty() ? "未提供" : expectedAnswer
            );
            
            String evaluation = llmClient.chatSimple(
                "你是答案评估专家。评估答案质量，返回格式：分数|评语。例如：85|回答正确，但可以更详细。",
                prompt
            );
            
            int score = 60;
            String feedback = "回答基本正确";
            
            if (evaluation != null && evaluation.contains("|")) {
                String[] parts = evaluation.split("[|]", 2);
                try {
                    score = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException ignored) {}
                feedback = parts.length > 1 ? parts[1].trim() : feedback;
            }
            
            Map<String, Object> result = Map.of(
                    "score", score,
                    "feedback", feedback,
                    "passed", score >= 60,
                    "summary", "得分: " + score + "分 - " + feedback
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("评估失败: " + e.getMessage());
        }
    }
}
