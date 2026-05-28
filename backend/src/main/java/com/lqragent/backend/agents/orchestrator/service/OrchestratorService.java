package com.lqragent.backend.agents.orchestrator.service;

import com.lqragent.backend.agents.orchestrator.dto.IntentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 意图识别与路由服务。
 * <p>
 * P5-2：优先调 LLM 意图分类，不可用时降级为关键词匹配。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final LlmIntentClassifier llmClassifier;

    /** 意图 → 关键词列表（按优先级排列） */
    private static final Map<String, List<String>> INTENT_KEYWORDS = Map.of(
        IntentResult.LEARNING_PATH, List.of(
                "学习路径", "学习路线", "怎么学", "学什么", "规划", "路线",
                "学习计划", "从哪开始", "下一步学", "学习顺序"),
        IntentResult.RESOURCE_GENERATE, List.of(
                "生成讲义", "出题", "练习题", "生成题目", "生成资源",
                "知识点讲解", "给我讲", "详细讲解", "生成代码", "示例代码"),
        IntentResult.MEDIA_GENERATE, List.of(
                "画图", "生成图片", "示意图", "流程图", "思维导图",
                "画一个", "图示", "可视化"),
        IntentResult.GREETING, List.of(
                "你好", "您好", "hi", "hello", "hey", "早上好", "下午好",
                "晚上好", "在吗", "在不在"),
        IntentResult.HELP, List.of(
                "帮助", "功能", "你能做什么", "怎么用", "命令",
                "使用说明", "help", "支持什么"),
        IntentResult.QA_QUESTION, List.of(
                "什么是", "怎么用", "为什么", "如何", "区别", "比较",
                "解释", "介绍", "说明", "含义", "作用", "原理")
    );

    /**
     * 识别用户消息意图。
     * 优先调 LLM，失败时降级为关键词匹配。
     */
    public IntentResult determineIntent(String message) {
        if (message == null || message.isBlank()) {
            return unknown("消息为空");
        }

        // P5-2: 优先 LLM 分类
        IntentResult llmResult = llmClassifier.classify(message);
        if (llmResult != null) {
            log.debug("[Orchestrator] LLM intent={}, confidence={}", llmResult.getIntent(), llmResult.getConfidence());
            return llmResult;
        }

        // 降级：关键词匹配
        log.debug("[Orchestrator] LLM 不可用，使用关键词规则");
        return keywordMatch(message);
    }

    /** 关键词匹配降级 */
    private IntentResult keywordMatch(String message) {
        String lower = message.toLowerCase().trim();
        for (var entry : INTENT_KEYWORDS.entrySet()) {
            String intent = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword.toLowerCase())) {
                    double confidence = 0.6 + (keyword.length() / (double) Math.max(lower.length(), 1)) * 0.3;
                    confidence = Math.min(confidence, 0.95);
                    String label = switch (intent) {
                        case IntentResult.QA_QUESTION -> "解答问题";
                        case IntentResult.LEARNING_PATH -> "学习路径规划";
                        case IntentResult.RESOURCE_GENERATE -> "生成学习资源";
                        case IntentResult.MEDIA_GENERATE -> "生成媒体内容";
                        case IntentResult.GREETING -> "问候";
                        case IntentResult.HELP -> "帮助说明";
                        default -> "未知意图";
                    };
                    log.debug("[Orchestrator] keyword intent={}, keyword={}, confidence={}", intent, keyword, confidence);
                    return IntentResult.builder()
                            .intent(intent)
                            .label(label)
                            .confidence(confidence)
                            .actionable(true)
                            .build();
                }
            }
        }
        log.debug("[Orchestrator] 未匹配关键词，默认 qa_question");
        return IntentResult.builder()
                .intent(IntentResult.QA_QUESTION)
                .label("解答问题")
                .confidence(0.4)
                .actionable(true)
                .build();
    }

    private IntentResult unknown(String reason) {
        return IntentResult.builder()
                .intent(IntentResult.UNKNOWN)
                .label("无法识别")
                .confidence(0)
                .actionable(false)
                .build();
    }
}
