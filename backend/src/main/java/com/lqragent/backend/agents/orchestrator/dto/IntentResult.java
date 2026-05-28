package com.lqragent.backend.agents.orchestrator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 意图识别结果。
 */
@Data
@Builder
@AllArgsConstructor
public class IntentResult {

    /** 意图标识 */
    private String intent;

    /** 可读标签（前端显示） */
    private String label;

    /** 置信度 0-1 */
    private double confidence;

    /** 是否应该执行（而非仅识别） */
    private boolean actionable;

    // 预定义意图常量
    public static final String QA_QUESTION = "qa_question";
    public static final String LEARNING_PATH = "learning_path";
    public static final String RESOURCE_GENERATE = "resource_generate";
    public static final String MEDIA_GENERATE = "media_generate";
    public static final String GREETING = "greeting";
    public static final String HELP = "help";
    public static final String UNKNOWN = "unknown";
}
