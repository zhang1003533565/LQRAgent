package com.lqragent.backend.agents.learn.spacedrepetition.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GetReviewScheduleTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_review_schedule"; }
    
    @Override
    public String description() { return "基于遗忘曲线计算真实的复习计划"; }
    
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
            
            // 统计每个知识点的最后答题时间和正确率
            Map<String, int[]> kpStats = new LinkedHashMap<>();
            Map<String, LocalDateTime> lastAttempt = new HashMap<>();
            
            LocalDateTime now = LocalDateTime.now();
            for (var record : records) {
                String kpId = record.getKpId();
                if (kpId == null || kpId.isBlank()) continue;
                
                kpStats.computeIfAbsent(kpId, k -> new int[]{0, 0, 999});
                kpStats.get(kpId)[1]++;
                if (Boolean.TRUE.equals(record.getIsCorrect())) {
                    kpStats.get(kpId)[0]++;
                }
                
                if (record.getCreatedAt() != null) {
                    if (!lastAttempt.containsKey(kpId) || record.getCreatedAt().isAfter(lastAttempt.get(kpId))) {
                        lastAttempt.put(kpId, record.getCreatedAt());
                    }
                }
            }
            
            // 计算每个知识点的复习优先级
            List<Map<String, Object>> schedule = new ArrayList<>();
            for (var entry : kpStats.entrySet()) {
                String kpId = entry.getKey();
                int correct = entry.getValue()[0];
                int total = entry.getValue()[1];
                double rate = total > 0 ? (double) correct / total : 0;
                
                // 计算距离上次答题的天数
                int daysSince = 7;
                if (lastAttempt.containsKey(kpId)) {
                    daysSince = (int) java.time.Duration.between(lastAttempt.get(kpId), now).toDays();
                }
                
                // 复习优先级：正确率低 + 时间久 = 优先复习
                double priority = (1 - rate) * 50 + daysSince * 5;
                
                if (priority > 20) {
                    schedule.add(Map.of(
                            "kpId", kpId,
                            "correctRate", (int) Math.round(rate * 100),
                            "daysSinceLastReview", daysSince,
                            "priority", (int) Math.round(priority),
                            "totalAttempts", total
                    ));
                }
            }
            
            // 按优先级排序
            schedule.sort((a, b) -> {
                int pA = (int) a.get("priority");
                int pB = (int) b.get("priority");
                return Integer.compare(pB, pA);
            });
            
            // 限制返回数量
            if (schedule.size() > 5) {
                schedule = schedule.subList(0, 5);
            }
            
            // 构建格式化内容
            StringBuilder content = new StringBuilder();
            content.append("📅 **复习计划**\n\n");
            
            if (schedule.isEmpty()) {
                content.append("太棒了！不需要复习，所有知识点都掌握得很好！\n");
            } else {
                for (int i = 0; i < schedule.size(); i++) {
                    var item = schedule.get(i);
                    content.append(i + 1).append(". ").append(item.get("kpId"));
                    content.append("（").append(item.get("daysSinceLastReview")).append(" 天前复习）\n");
                }
            }
            
            String summary = schedule.isEmpty() 
                    ? "不需要复习"
                    : "有 " + schedule.size() + " 个知识点需要复习";
            
            AgentResponse response = AgentResponse.withData(
                    "review_schedule",
                    "复习计划",
                    summary,
                    content.toString(),
                    Map.of("userId", userId, "schedule", schedule, "totalItems", schedule.size())
            );
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("计算复习计划失败: " + e.getMessage());
        }
    }
}
