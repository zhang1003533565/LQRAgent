package com.lqragent.backend.agents.knowledgestate.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetKnowledgeStateTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_knowledge_state"; }
    
    @Override
    public String description() { return "获取用户的知识状态，包括各知识点的掌握度"; }
    
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
            
            var records = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
            
            if (records.isEmpty()) {
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "states", java.util.List.of(),
                        "summary", "暂无学习记录"
                )));
            }
            
            // 统计每个知识点
            Map<String, int[]> stats = new java.util.HashMap<>();
            for (var record : records) {
                String kpId = record.getKpId() != null ? record.getKpId() : "unknown";
                stats.computeIfAbsent(kpId, k -> new int[]{0, 0});
                stats.get(kpId)[1]++;
                if (record.getIsCorrect() || (record.getScore() != null && record.getScore() >= 60)) {
                    stats.get(kpId)[0]++;
                }
            }
            
            // 构建状态列表
            java.util.List<Map<String, Object>> states = new java.util.ArrayList<>();
            for (var entry : stats.entrySet()) {
                int correct = entry.getValue()[0];
                int total = entry.getValue()[1];
                double mastery = total > 0 ? (double) correct / total : 0;
                
                states.add(Map.of(
                        "kpId", entry.getKey(),
                        "mastery", Math.round(mastery * 100),
                        "totalAttempts", total,
                        "correctAttempts", correct,
                        "level", mastery >= 0.8 ? "掌握" : mastery >= 0.6 ? "理解" : "薄弱"
                ));
            }
            
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "userId", userId,
                    "states", states,
                    "totalKnowledgePoints", states.size(),
                    "summary", "分析了 " + states.size() + " 个知识点"
            )));
        } catch (Exception e) {
            return ToolResult.failure("获取知识状态失败: " + e.getMessage());
        }
    }
}
