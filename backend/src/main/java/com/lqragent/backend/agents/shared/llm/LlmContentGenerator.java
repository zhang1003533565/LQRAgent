package com.lqragent.backend.agents.shared.llm;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 直调大模型 API 生成教学资源（不再经过 ai-server）。
 * <p>
 * 复用了 ModelConfigService 中的 LLM 调用方式（兼容 OpenAI API）。
 * 不可用时返回 null，由调用方决定模板兜底。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmContentGenerator {

    private final AppRuntimeConfig runtimeConfig;

    private static final Map<String, String> SYSTEM_PROMPTS = Map.of(
            "lesson",
            "你是一位 Python 编程课程的资深讲师。根据给定的知识点名称和描述，"
            + "生成一份结构清晰的讲义。包含：学习目标、核心概念讲解、语法示例、"
            + "注意事项、小结。使用 Markdown 格式，适当使用代码块。",

            "quiz",
            "你是一位 Python 编程课程的出题老师。根据给定的知识点名称和描述，"
            + "生成一套练习题。包含：1 道选择题（含4个选项）、1 道填空题、"
            + "1 道编程题。使用 Markdown 格式。",

            "code_case",
            "你是一位 Python 编程课程的讲师。根据给定的知识点名称和描述，"
            + "生成一份可直接运行的代码示例。包含：用途说明、完整可运行代码、"
            + "运行说明。使用 Markdown 格式，代码放在 ```python 代码块中。",

            "mind_map",
            "你是一位 Python 编程课程的课程设计师。根据给定的知识点名称，"
            + "生成一份 Markdown 列表格式的思维导图。以知识点名称为中心主题，"
            + "列出 3-6 个子主题，每个子主题下列出 2-4 个具体要点。"
            + "输出格式：\n- 中心主题\n  - 子主题1\n    - 要点1.1\n    - 要点1.2",

            "extended_reading",
            "你是一位 Python 编程课程的教学顾问。根据给定的知识点名称，"
            + "生成一份拓展阅读材料。包含：学习建议、推荐阅读方向、"
            + "实践项目建议（2-3 个）、常见误区提醒。使用 Markdown 格式。",

            "mermaid",
            "你是一位 Python 编程课程的讲师。根据用户的问题和回答，"
            + "生成 Mermaid 流程图代码（graph TD 格式）。只输出 Mermaid 代码，不要解释。",

            "factual_check",
            "你是一个内容质检员。检查以下教学内容是否有事实错误。"
            + "如果发现明显错误，输出 'FAIL: 原因'；"
            + "如果没有明显错误，输出 'PASS'。只输出一行。",

            "weakness_analysis",
            "你是一个学习效果分析专家。根据学生的学习行为记录，分析薄弱知识点、学习效率、建议改进方向。"
            + "输出格式：\n薄弱点：知识点A、知识点B\n学习效率：高/中/低\n建议：xxx",

            "profile_extract",
            "你是一个学生画像分析助手。根据学生的对话记录，以 JSON 格式输出以下6个维度及知识点掌握增量：\n"
            + "{\n"
            + "  \"knowledge_base\": \"初学/基础/进阶/熟练\",\n"
            + "  \"learning_goal\": \"目标描述\",\n"
            + "  \"cognitive_style\": \"视觉型/听觉型/动手型/阅读型\",\n"
            + "  \"weakness\": [\"易错点1\"],\n"
            + "  \"learning_pace\": \"快/中/慢\",\n"
            + "  \"interest\": \"兴趣方向\",\n"
            + "  \"mastered_topics\": [\"学生明确说已掌握的知识点，如 for循环\"],\n"
            + "  \"pending_topics\": [\"学生明确说未掌握/想学的知识点，如 装饰器\"]\n"
            + "}\n只输出 JSON，不要解释。若对话提到「学过X但不懂Y」，必须把 X 放入 mastered_topics，Y 放入 pending_topics。"
    );

    private static final Map<String, String> USER_PROMPTS = Map.of(
            "lesson",
            "知识点名称：{title}\n知识点描述：{description}\n\n请生成一份完整的 Markdown 格式讲义。",

            "quiz",
            "知识点名称：{title}\n知识点描述：{description}\n\n请生成一套练习题（选择+填空+编程）。",

            "code_case",
            "知识点名称：{title}\n知识点描述：{description}\n\n请生成一份可运行的代码示例。",

            "mind_map",
            "知识点名称：{title}\n\n请以该名称为中心主题，生成 Markdown 列表格式的思维导图。",

            "extended_reading",
            "知识点名称：{title}\n知识点描述：{description}\n\n请生成拓展阅读材料。"
    );

    /**
     * 调用 LLM 生成资源内容。
     *
     * @param type       资源类型：lesson / quiz / code_case
     * @param title      知识点名称
     * @param description 知识点描述
     * @return 生成的内容 Markdown 字符串，失败时返回 null
     */
    public String generate(String type, String title, String description) {
        String host = runtimeConfig.get(ConfigKeys.LLM_HOST);
        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY);
        String model = runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini");

        if (host == null || host.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("[LlmContentGenerator] LLM 未配置，跳过 LLM 生成");
            return null;
        }

        String systemPrompt = SYSTEM_PROMPTS.getOrDefault(type, SYSTEM_PROMPTS.get("lesson"));
        String userPrompt = USER_PROMPTS.getOrDefault(type, USER_PROMPTS.get("lesson"))
                .replace("{title}", title)
                .replace("{description}", description != null ? description : title);

        String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        String url = base + "/chat/completions";

        try {
            RestClient client = RestClient.builder().build();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", model,
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)
                            )
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        String content = (String) message.get("content");
                        if (content != null && !content.isBlank()) {
                            log.info("[LlmContentGenerator] 生成成功: type={}, title={}, len={}",
                                    type, title, content.length());
                            return content;
                        }
                    }
                }
            }
            log.warn("[LlmContentGenerator] LLM 返回空结果: type={}, title={}", type, title);
            return null;
        } catch (Exception e) {
            log.warn("[LlmContentGenerator] LLM 调用失败: type={}, title={}, error={}",
                    type, title, e.getMessage());
            return null;
        }
    }
}
