package com.lqragent.backend.agents.intervention.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class GetInterventionTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepo;
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_intervention"; }
    
    @Override
    public String description() { return "基于学习数据识别问题并提供干预建议"; }
    
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
            
            // 获取答题记录
            var records = quizRecordRepo.findByUserIdOrderByCreatedAtDesc(userId);
            
            if (records.isEmpty()) {
                AgentResponse response = AgentResponse.success(
                        "intervention",
                        "学习状态分析",
                        "暂无学习记录，建议开始学习"
                );
                return ToolResult.success(response.toJson());
            }
            
            // 分析问题
            List<String> issues = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();
            
            // 计算正确率
            int totalCorrect = 0;
            for (var r : records) {
                if (Boolean.TRUE.equals(r.getIsCorrect())) totalCorrect++;
            }
            double rate = (double) totalCorrect / records.size();
            
            if (rate < 0.5) {
                issues.add("整体正确率偏低(" + Math.round(rate * 100) + "%)");
                suggestions.add("建议回顾基础知识，降低学习难度");
            }
            
            // 检查连续错误
            int consecutiveErrors = 0;
            for (var r : records) {
                if (!Boolean.TRUE.equals(r.getIsCorrect())) {
                    consecutiveErrors++;
                } else {
                    break;
                }
            }
            if (consecutiveErrors >= 3) {
                issues.add("连续答错" + consecutiveErrors + "题");
                suggestions.add("建议休息一下，调整学习状态");
            }
            
            // 调用 LLM 生成干预建议
            String prompt = String.format(
                "用户学习数据：\n- 总题数: %d\n- 正确率: %d%%\n- 连续错误: %d\n- 发现问题: %s\n\n请给出具体的学习干预建议。",
                records.size(), Math.round(rate * 100), consecutiveErrors,
                issues.isEmpty() ? "无" : String.join(", ", issues)
            );
            
            String advice = llmClient.chatSimple(
                "你是学习辅导专家。根据学生的学习数据，给出具体、可执行的学习建议。简洁明了。",
                prompt
            );
            
            if (issues.isEmpty()) {
                issues.add("学习状态良好");
            }
            if (suggestions.isEmpty()) {
                suggestions.add(advice != null ? advice.trim() : "继续保持当前学习节奏");
            }
            
            // 构建格式化内容
            StringBuilder content = new StringBuilder();
            content.append("🔍 **学习状态分析**\n\n");
            content.append("答题次数：").append(records.size()).append("\n");
            content.append("正确率：").append(Math.round(rate * 100)).append("%\n\n");
            
            if (!issues.isEmpty()) {
                content.append("**发现：**\n");
                for (String issue : issues) {
                    content.append("- ").append(issue).append("\n");
                }
                content.append("\n");
            }
            
            if (!suggestions.isEmpty()) {
                content.append("**建议：**\n");
                for (String sug : suggestions) {
                    content.append("- ").append(sug).append("\n");
                }
            }
            
            AgentResponse response = AgentResponse.withData(
                    "intervention",
                    "学习状态分析",
                    issues.get(0),
                    content.toString(),
                    Map.of("userId", userId, "accuracy", Math.round(rate * 100))
            );
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("分析失败: " + e.getMessage());
        }
    }
}
