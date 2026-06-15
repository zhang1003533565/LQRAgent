package com.lqragent.backend.agents.difficulty.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AdjustDifficultyTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "adjust_difficulty"; }
    
    @Override
    public String description() { return "基于用户答题表现推荐合适的难度级别"; }
    
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
            
            // 获取最近答题记录
            var records = quizRecordRepo.findByUserIdOrderByCreatedAtDesc(userId);
            
            if (records.isEmpty()) {
                AgentResponse response = AgentResponse.success(
                        "difficulty",
                        "难度推荐",
                        "暂无答题记录，建议从基础开始"
                );
                return ToolResult.success(response.toJson());
            }
            
            // 计算最近正确率
            int recentCount = Math.min(records.size(), 20);
            int recentCorrect = 0;
            for (int i = 0; i < recentCount; i++) {
                if (Boolean.TRUE.equals(records.get(i).getIsCorrect())) {
                    recentCorrect++;
                }
            }
            double recentRate = (double) recentCorrect / recentCount;
            
            // 计算总体正确率
            int totalCorrect = 0;
            for (var record : records) {
                if (Boolean.TRUE.equals(record.getIsCorrect())) {
                    totalCorrect++;
                }
            }
            double totalRate = (double) totalCorrect / records.size();
            
            // 推荐难度
            String level;
            String reason;
            
            if (recentRate >= 0.8) {
                level = "hard";
                reason = "最近正确率" + Math.round(recentRate * 100) + "%，表现优秀，推荐挑战高难度";
            } else if (recentRate >= 0.6) {
                level = "medium";
                reason = "最近正确率" + Math.round(recentRate * 100) + "%，建议保持中等难度";
            } else {
                level = "easy";
                reason = "最近正确率" + Math.round(recentRate * 100) + "%，建议巩固基础";
            }
            
            // 构建格式化内容
            StringBuilder content = new StringBuilder();
            content.append("🎯 **难度推荐**\n\n");
            content.append("当前正确率：").append(Math.round(recentRate * 100)).append("%\n");
            content.append("推荐难度：**").append(level).append("**\n\n");
            content.append(reason);
            
            AgentResponse response = AgentResponse.withData(
                    "difficulty",
                    "难度推荐",
                    "推荐难度: " + level,
                    content.toString(),
                    Map.of(
                            "userId", userId,
                            "recommendedLevel", level,
                            "recentAccuracy", Math.round(recentRate * 100),
                            "totalAttempts", records.size()
                    )
            );
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("分析失败: " + e.getMessage());
        }
    }
}
