package com.lqragent.backend.agents.quiz.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.LlmClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GenerateQuizTool implements AgentTool {

    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() {
        return "generate_quiz";
    }

    @Override
    public String description() {
        return "生成结构化练习题，支持选择题、判断题、填空题、简答题、编程题等混合题型";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "出题主题或知识点"),
                        "context", Map.of("type", "string", "description", "知识库检索内容或补充材料，可选"),
                        "count", Map.of("type", "integer", "description", "题目数量，默认5"),
                        "difficulty", Map.of("type", "string", "description", "难度：简单/中等/困难"),
                        "questionTypes", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string"),
                                "description", "题型列表，如选择题、判断题、填空题、简答题、编程题"
                        )
                ),
                "required", new String[]{"topic"}
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String topic = getString(args, "topic", "Python基础");
            String context = getString(args, "context", "");
            int count = getInt(args, "count", 5);
            String difficulty = getString(args, "difficulty", "中等");
            Object questionTypes = args.getOrDefault("questionTypes", List.of("选择题", "填空题", "简答题"));

            String userPrompt = String.format("""
                    请生成练习题，要求如下：
                    - 主题：%s
                    - 数量：%d
                    - 难度：%s
                    - 题型：%s
                    - 参考材料：%s

                    必须只返回 JSON，不要包 Markdown 代码块。格式如下：
                    {
                      "title": "练习标题",
                      "topic": "主题",
                      "difficulty": "简单/中等/困难",
                      "questions": [
                        {
                          "id": 1,
                          "type": "选择题/判断题/填空题/简答题/编程题",
                          "stem": "题干",
                          "options": ["A. ...", "B. ..."],
                          "answer": "正确答案",
                          "explanation": "解析",
                          "difficulty": "简单/中等/困难"
                        }
                      ]
                    }
                    """, topic, count, difficulty, questionTypes, context.isBlank() ? "无" : context);

            String quizJson = llmClient.chatSimple(
                    "你是练习题生成专家。题目必须准确、可作答、难度匹配。只输出严格 JSON。",
                    userPrompt
            );

            Object data = parseOrWrap(quizJson, topic, difficulty);
            AgentResponse response = AgentResponse.withData(
                    "quiz",
                    topic + "练习题",
                    "已生成「" + topic + "」练习题",
                    quizJson,
                    data
            );
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("生成题目失败: " + e.getMessage());
        }
    }

    private Object parseOrWrap(String quizJson, String topic, String difficulty) {
        try {
            if (quizJson != null && !quizJson.isBlank()) {
                return mapper.readValue(stripCodeFence(quizJson), Object.class);
            }
        } catch (Exception ignored) {
        }
        return Map.of(
                "title", topic + "练习题",
                "topic", topic,
                "difficulty", difficulty,
                "questions", List.of(),
                "raw", quizJson == null ? "" : quizJson
        );
    }

    private String stripCodeFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private String getString(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
