package com.lqragent.backend.orchestrator.service;

import com.lqragent.backend.orchestrator.dto.IntentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 意图识别与路由服务。
 * 基于关键词匹配识别用户意图（P1 关键词版本，后续可替换为 LLM 意图分类 P4-3）。
 */
@Slf4j
@Service
public class OrchestratorService {

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
     *
     * @param message 用户输入文本
     * @return 识别结果
     */
    public IntentResult determineIntent(String message) {
        if (message == null || message.isBlank()) {
            return unknown("消息为空");
        }

        String lower = message.toLowerCase().trim();

        // 按 INTENT_KEYWORDS 顺序匹配
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
                    log.debug("[Orchestrator] intent={}, keyword={}, confidence={}", intent, keyword, confidence);
                    return IntentResult.builder()
                            .intent(intent)
                            .label(label)
                            .confidence(confidence)
                            .actionable(true)
                            .build();
                }
            }
        }

        // 没匹配到任何关键词 → 默认当作答疑问题
        log.debug("[Orchestrator] 未匹配关键词，默认 qa_question: {}", message);
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
