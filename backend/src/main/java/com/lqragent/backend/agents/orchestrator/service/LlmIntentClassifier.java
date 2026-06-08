package com.lqragent.backend.agents.orchestrator.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lqragent.backend.agents.orchestrator.dto.IntentResult;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM 意图分类器。
 * <p>
 * 调用已配置的大模型判断用户意图，替代 P1 关键词规则。
 * 不可用时返回 null，由 caller 降级回关键词匹配。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmIntentClassifier {

    private final AppRuntimeConfig runtimeConfig;

    private static final String SYSTEM_PROMPT = """
            你是一个学习助手系统的意图分类器。根据用户输入，只返回以下之一：
            
            learning_path    — 想学某项技能/知识（如"我想学Python"、"教我编程"、"零基础入门"）
            resource_generate — 生成讲义/题目/代码等学习资源
            media_generate    — 生成图片/流程图/思维导图
            qa_question      — 提问/答疑（关于概念、用法、原理、区别等）
            greeting          — 纯粹问候，没有任何学习相关意图
            help              — 询问功能/使用帮助
            
            重要规则：
            1. 如果消息同时包含问候和学习意图（如"你好，我想学Python"），返回学习意图，不要返回greeting
            2. 只有纯粹的问候（如"你好"、"在吗"）才返回greeting
            3. "我想学"、"想学习"、"教我"、"入门"等表达都表示learning_path意图
            
            只输出意图名称，不要任何其他文字。
            """;

    private static final Pattern RESPONSE_PATTERN = Pattern.compile(
            "(qa_question|learning_path|resource_generate|media_generate|greeting|help|unknown)"
    );

    /** 标签映射 */
    private static final Map<String, String> LABELS = Map.of(
            IntentResult.QA_QUESTION, "解答问题",
            IntentResult.LEARNING_PATH, "学习路径规划",
            IntentResult.RESOURCE_GENERATE, "生成学习资源",
            IntentResult.MEDIA_GENERATE, "生成媒体内容",
            IntentResult.GREETING, "问候",
            IntentResult.HELP, "帮助说明"
    );

    /**
     * 用 LLM 识别用户意图。
     *
     * @param message 用户输入
     * @return 意图结果，LLM 不可用时返回 null
     */
    public IntentResult classify(String message) {
        String host = runtimeConfig.get(ConfigKeys.LLM_HOST);
        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY);
        String model = runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini");

        if (host == null || host.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.debug("[LlmIntentClassifier] LLM 未配置，跳过");
            return null;
        }

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
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", message)
                            ),
                            "max_tokens", 32,
                            "temperature", 0
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return null;

            String reply = ((String) msg.get("content")).trim();
            var matcher = RESPONSE_PATTERN.matcher(reply);
            if (!matcher.find()) {
                log.warn("[LlmIntentClassifier] LLM 返回无法识别: {}", reply);
                return null;
            }

            String intent = matcher.group(1);
            log.info("[LlmIntentClassifier] intent={}, msg=\"{}\"", intent, message);
            return IntentResult.builder()
                    .intent(intent)
                    .label(LABELS.getOrDefault(intent, "未知"))
                    .confidence(0.85)
                    .actionable(true)
                    .build();

        } catch (Exception e) {
            log.warn("[LlmIntentClassifier] LLM 调用失败: {}", e.getMessage());
            return null;
        }
    }
}
