package com.lqragent.backend.agents.learningstyle.tools;

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
public class DetectLearningStyleTool implements AgentTool {
    
    private final QuizRecordRepository quizRecordRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "detect_learning_style"; }
    
    @Override
    public String description() { return "基于用户行为数据识别学习风格"; }
    
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
            
            // 获取答题记录分析学习风格
            var records = quizRecordRepo.findByUserIdOrderByCreatedAtDesc(userId);
            
            String style;
            String description;
            List<String> recommendations;
            
            if (records.isEmpty()) {
                style = "visual";
                description = "暂无足够数据判断学习风格，默认推荐视觉型学习";
                recommendations = List.of("使用思维导图", "观看视频教程", "查看图表说明");
            } else {
                // 基于答题模式分析
                int total = records.size();
                int correct = 0;
                for (var r : records) {
                    if (Boolean.TRUE.equals(r.getIsCorrect())) correct++;
                }
                double rate = (double) correct / total;
                
                if (rate >= 0.7) {
                    style = "reading";
                    description = "您答题正确率较高，可能是阅读型学习者，善于通过文字理解概念";
                    recommendations = List.of("阅读详细讲义", "做笔记总结", "参考文档学习");
                } else if (rate >= 0.5) {
                    style = "visual";
                    description = "您的学习效果中等，建议结合图表和视频辅助理解";
                    recommendations = List.of("使用思维导图", "观看视频教程", "查看示例代码");
                } else {
                    style = "kinesthetic";
                    description = "您可能更适合动手实践型学习，通过练习加深理解";
                    recommendations = List.of("多做练习题", "动手写代码", "参与项目实战");
                }
            }
            
            // 构建格式化内容
            StringBuilder content = new StringBuilder();
            content.append("🎨 **学习风格分析**\n\n");
            content.append("您的学习风格：**").append(style).append("**\n\n");
            content.append(description).append("\n\n");
            
            if (!recommendations.isEmpty()) {
                content.append("**建议：**\n");
                for (String rec : recommendations) {
                    content.append("- ").append(rec).append("\n");
                }
            }
            
            AgentResponse response = AgentResponse.withData(
                    "learning_style",
                    "学习风格分析",
                    "您的学习风格: " + style,
                    content.toString(),
                    Map.of("userId", userId, "style", style)
            );
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("分析失败: " + e.getMessage());
        }
    }
}
