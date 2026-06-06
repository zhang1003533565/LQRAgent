package com.lqragent.backend.agents.learn.state.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyzeWeaknessTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "analyze_weakness"; }
    
    @Override
    public String description() { return "分析用户薄弱知识点，基于答题记录计算正确率"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "integer", "description", "用户ID"),
                        "kpId", Map.of("type", "string", "description", "知识点ID（可选）")
                ),
                "required", new String[]{"userId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Long userId = Long.parseLong(args.get("userId").toString());
            String kpId = args.get("kpId") != null ? args.get("kpId").toString() : null;
            
            // 获取答题记录
            var records = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
            
            if (records.isEmpty()) {
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "weakPoints", java.util.List.of(),
                        "summary", "暂无答题记录"
                )));
            }
            
            // 统计每个知识点的正确率
            Map<String, int[]> stats = new java.util.HashMap<>(); // [correct, total]
            for (var record : records) {
                String key = record.getKpId() != null ? record.getKpId() : "unknown";
                stats.computeIfAbsent(key, k -> new int[]{0, 0});
                stats.get(key)[1]++;
                if (record.getIsCorrect() || (record.getScore() != null && record.getScore() >= 60)) {
                    stats.get(key)[0]++;
                }
            }
            
            // 找出薄弱知识点（正确率 < 60%）
            java.util.List<Map<String, Object>> weakPoints = new java.util.ArrayList<>();
            for (var entry : stats.entrySet()) {
                int correct = entry.getValue()[0];
                int total = entry.getValue()[1];
                double rate = total > 0 ? (double) correct / total : 0;
                
                if (rate < 0.6) {
                    weakPoints.add(Map.of(
                            "kpId", entry.getKey(),
                            "correctRate", Math.round(rate * 100),
                            "totalAttempts", total,
                            "correctAttempts", correct
                    ));
                }
            }
            
            // 按正确率排序
            weakPoints.sort((a, b) -> {
                double rateA = (double) a.get("correctRate");
                double rateB = (double) b.get("correctRate");
                return Double.compare(rateA, rateB);
            });
            
            // 如果指定了 kpId，只返回该知识点
            if (kpId != null) {
                weakPoints.removeIf(wp -> !wp.get("kpId").equals(kpId));
            }
            
            String summary = weakPoints.isEmpty() 
                    ? "没有发现薄弱知识点" 
                    : "发现 " + weakPoints.size() + " 个薄弱知识点";
            
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "userId", userId,
                    "weakPoints", weakPoints,
                    "totalKnowledgePoints", stats.size(),
                    "summary", summary
            )));
        } catch (Exception e) {
            return ToolResult.failure("分析薄弱点失败: " + e.getMessage());
        }
    }
}
