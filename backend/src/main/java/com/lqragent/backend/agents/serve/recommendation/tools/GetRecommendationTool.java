package com.lqragent.backend.agents.serve.recommendation.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetRecommendationTool implements AgentTool {
    
    private final LearnerProfileService profileService;
    private final QuizRecordRepository quizRecordRepo;
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_recommendation"; }
    
    @Override
    public String description() { return "基于用户画像和学习记录，推荐个性化学习资源"; }
    
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
            
            // 获取用户画像
            var profile = profileService.getSummary(userId);
            String level = profile.getKnowledgeLevel();
            String goal = profile.getLearningGoal();
            
            // 获取最近答题记录
            var recentQuiz = quizRecordRepo.findByUserIdOrderByCreatedAtDesc(userId);
            int recentCorrect = 0;
            int recentTotal = Math.min(recentQuiz.size(), 10);
            for (int i = 0; i < recentTotal; i++) {
                if (recentQuiz.get(i).getIsCorrect()) recentCorrect++;
            }
            
            // 调用 LLM 生成个性化推荐
            String prompt = String.format(
                "用户信息：\n- 知识水平: %s\n- 学习目标: %s\n- 最近%d题正确率: %d%%\n\n" +
                "请为该用户推荐3个学习资源，返回JSON格式：\n" +
                "[{\"type\": \"lesson/quiz/code\", \"title\": \"标题\", \"reason\": \"推荐理由\"}]",
                level, goal != null ? goal : "未设置", recentTotal, 
                recentTotal > 0 ? recentCorrect * 100 / recentTotal : 0
            );
            
            String llmResult = llmClient.chatSimple(
                "你是学习资源推荐专家。根据用户画像推荐合适的学习资源。只返回JSON数组，不要其他内容。",
                prompt
            );
            
            // 解析 LLM 结果
            String recommendations = llmResult != null ? llmResult.trim() : "[]";
            recommendations = recommendations.replaceAll("```json", "").replaceAll("```", "").trim();
            
            // 构建格式化内容
            StringBuilder content = new StringBuilder();
            content.append("📊 **个性化推荐**\n\n");
            content.append("根据您的学习情况（").append(level != null ? level : "初级");
            content.append("，正确率 ").append(recentTotal > 0 ? recentCorrect * 100 / recentTotal : 0).append("%）：\n\n");
            
            // 解析推荐列表
            try {
                var recArray = mapper.readTree(recommendations);
                for (int i = 0; i < recArray.size(); i++) {
                    var rec = recArray.get(i);
                    content.append(i + 1).append(". **").append(rec.path("title").asText("")).append("**\n");
                    content.append("   ").append(rec.path("reason").asText("")).append("\n\n");
                }
            } catch (Exception e) {
                content.append(recommendations).append("\n");
            }
            
            AgentResponse response = AgentResponse.withData(
                    "recommendation",
                    "个性化推荐",
                    "基于您的学习情况，为您推荐以下资源",
                    content.toString(),
                    Map.of(
                            "userId", userId,
                            "knowledgeLevel", level != null ? level : "BEGINNER",
                            "recentAccuracy", recentTotal > 0 ? recentCorrect * 100 / recentTotal : 0
                    )
            );
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("推荐失败: " + e.getMessage());
        }
    }
}
