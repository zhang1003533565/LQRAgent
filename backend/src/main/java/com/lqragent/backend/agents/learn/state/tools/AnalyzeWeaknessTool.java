package com.lqragent.backend.agents.learn.state.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class AnalyzeWeaknessTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepo;
    private final AiServerWsProxy aiServerWsProxy;
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "analyze_weakness"; }
    
    @Override
    public String description() { return "调用 ai-server 分析用户薄弱知识点"; }
    
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
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "userId", userId,
                        "weakPoints", List.of(),
                        "summary", "暂无答题记录，无法分析薄弱点"
                )));
            }
            
            // 统计每个知识点
            Map<String, int[]> stats = new HashMap<>();
            for (var record : records) {
                String kpId = record.getKpId();
                if (kpId == null || kpId.isBlank()) continue;
                stats.computeIfAbsent(kpId, k -> new int[]{0, 0});
                stats.get(kpId)[1]++;
                if (Boolean.TRUE.equals(record.getIsCorrect())) {
                    stats.get(kpId)[0]++;
                }
            }
            
            // 构建行为数据给 ai-server
            StringBuilder behaviorData = new StringBuilder();
            behaviorData.append("用户 ").append(userId).append(" 的答题数据：\n");
            for (var entry : stats.entrySet()) {
                int correct = entry.getValue()[0];
                int total = entry.getValue()[1];
                double rate = total > 0 ? (double) correct / total : 0;
                behaviorData.append("- ").append(entry.getKey())
                    .append(": ").append(correct).append("/").append(total)
                    .append(" (").append(Math.round(rate * 100)).append("%)\n");
            }
            
            // 调用 ai-server 分析薄弱点
            String analysis = aiServerWsProxy.analyzeWeakness(behaviorData.toString());
            
            // 找出薄弱知识点
            List<Map<String, Object>> weakPoints = new ArrayList<>();
            for (var entry : stats.entrySet()) {
                int correct = entry.getValue()[0];
                int total = entry.getValue()[1];
                double rate = total > 0 ? (double) correct / total : 0;
                if (rate < 0.6 && total >= 2) {
                    weakPoints.add(Map.of(
                            "kpId", entry.getKey(),
                            "correctRate", Math.round(rate * 100),
                            "totalAttempts", total
                    ));
                }
            }
            
            // 调用 LLM 生成建议
            String advice = analysis != null ? analysis : "建议重点复习薄弱知识点";
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "totalKnowledgePoints", stats.size(),
                    "weakPoints", weakPoints,
                    "advice", advice,
                    "source", "ai-server",
                    "summary", weakPoints.isEmpty() ? "没有薄弱知识点" : "发现 " + weakPoints.size() + " 个薄弱知识点"
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("分析失败: " + e.getMessage());
        }
    }
}
