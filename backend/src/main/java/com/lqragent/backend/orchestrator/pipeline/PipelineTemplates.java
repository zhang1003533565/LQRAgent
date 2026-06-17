package com.lqragent.backend.orchestrator.pipeline;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.orchestrator.AgentIds;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置 Pipeline 模板
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineTemplates {

    private final PipelineEngine engine;

    @PostConstruct
    public void init() {
        engine.registerTemplate(learningPath());
        engine.registerTemplate(questionAnswer());
        engine.registerTemplate(mediaGeneration());
        engine.registerTemplate(contentAnalysis());
        // 新增：单步 Agent Pipeline 模板
        engine.registerTemplate(resource());
        engine.registerTemplate(diagram());
        engine.registerTemplate(summary());
        engine.registerTemplate(quiz());
        engine.registerTemplate(profile());
        engine.registerTemplate(recommendation());
        engine.registerTemplate(assessment());
        engine.registerTemplate(intervention());
        engine.registerTemplate(learningPathCore());
        engine.registerTemplate(quizEvaluation());
        // 阶段三新增
        engine.registerTemplate(multimodalExplanation());
        engine.registerTemplate(learningLoop());
        log.info("[PipelineTemplates] registered {} templates", 16);
    }

    /**
     * 学习路径生成流水线
     * 获取用户画像 -> 生成路径 -> 生成资源 -> 质量评估
     */
    public static PipelineConfig learningPath() {
        return PipelineConfig.builder()
                .pipelineId("learning_path")
                .name("学习路径生成")
                .description("根据用户画像生成个性化学习路径")
                .steps(List.of(
                        // Step 1: 获取用户画像
                        PipelineStep.builder()
                                .stepId("profile")
                                .agentId(AgentIds.PROFILE)
                                .action("get_profile")
                                .build(),
                        // Step 2: 生成路径（依赖画像）
                        PipelineStep.builder()
                                .stepId("path_gen")
                                .agentId(AgentIds.LEARNING_PATH)
                                .action("generate_path")
                                .dependsOn(List.of("profile"))
                                .resultMapping(Map.of("profile", "profile"))
                                .build(),
                        // Step 3: 生成资源（依赖路径）
                        PipelineStep.builder()
                                .stepId("resources")
                                .agentId(AgentIds.RESOURCE)
                                .action("batch_generate")
                                .dependsOn(List.of("path_gen"))
                                .resultMapping(Map.of("path_gen", "path"))
                                .timeoutMs(60000)
                                .build(),
                        // Step 4: 质量评估（依赖资源）
                        PipelineStep.builder()
                                .stepId("quality")
                                .agentId(AgentIds.QUALITY)
                                .action("check")
                                .dependsOn(List.of("resources"))
                                .resultMapping(Map.of("resources", "resources"))
                                .optional(true)
                                .build(),
                        // Step 5: 效果评估（依赖资源，可选）
                        PipelineStep.builder()
                                .stepId("effect")
                                .agentId(AgentIds.EFFECT)
                                .action("evaluate")
                                .dependsOn(List.of("resources"))
                                .resultMapping(Map.of("resources", "resources"))
                                .optional(true)
                                .build()
                ))
                .build();
    }

    /**
     * 智能问答流水线
     * 并行：知识检索 + 意图分析 -> 生成答案
     */
    public static PipelineConfig questionAnswer() {
        return PipelineConfig.builder()
                .pipelineId("qa")
                .name("智能问答")
                .description("结合知识库和意图分析生成答案")
                .steps(List.of(
                        // 并行：知识检索
                        PipelineStep.builder()
                                .stepId("knowledge")
                                .agentId(AgentIds.QA)
                                .action("search_knowledge")
                                .build(),
                        // 并行：内容分析
                        PipelineStep.builder()
                                .stepId("analysis")
                                .agentId(AgentIds.CONTENT_ANALYSIS)
                                .action("analyze")
                                .build(),
                        // 生成答案（依赖两者）
                        PipelineStep.builder()
                                .stepId("answer")
                                .agentId(AgentIds.QA)
                                .action("generate_answer")
                                .dependsOn(List.of("knowledge", "analysis"))
                                .resultMapping(Map.of(
                                        "knowledge", "knowledgeContext",
                                        "analysis", "analysisResult"
                                ))
                                .build()
                ))
                .build();
    }

    /**
     * 媒体内容生成流水线
     * 生成 Prompt -> 生成媒体
     */
    public static PipelineConfig mediaGeneration() {
        return PipelineConfig.builder()
                .pipelineId("media_gen")
                .name("媒体内容生成")
                .description("根据需求生成媒体内容（图片、视频等）")
                .steps(List.of(
                        // Step 1: 生成 Prompt
                        PipelineStep.builder()
                                .stepId("prompt_gen")
                                .agentId(AgentIds.PROMPT_GEN)
                                .action("generate_prompt")
                                .build(),
                        // Step 2: 生成媒体（依赖 Prompt）
                        PipelineStep.builder()
                                .stepId("media_gen")
                                .agentId(AgentIds.MEDIA_GEN)
                                .action("generate_media")
                                .dependsOn(List.of("prompt_gen"))
                                .resultMapping(Map.of("prompt_gen", "prompt"))
                                .timeoutMs(120000)  // 媒体生成可能较慢
                                .build()
                ))
                .build();
    }

    /**
     * 内容分析流水线
     * 分析内容 -> 知识提取 -> 质量评估
     */
    public static PipelineConfig contentAnalysis() {
        return PipelineConfig.builder()
                .pipelineId("content_analysis")
                .name("内容分析")
                .description("分析上传内容，提取知识点并评估质量")
                .steps(List.of(
                        // Step 1: 内容分析
                        PipelineStep.builder()
                                .stepId("analyze")
                                .agentId(AgentIds.CONTENT_ANALYSIS)
                                .action("analyze_content")
                                .build(),
                        // Step 2: 知识状态更新（依赖分析）
                        PipelineStep.builder()
                                .stepId("knowledge_update")
                                .agentId(AgentIds.KNOWLEDGE_STATE)
                                .action("update_state")
                                .dependsOn(List.of("analyze"))
                                .resultMapping(Map.of("analyze", "analysisResult"))
                                .optional(true)
                                .build(),
                        // Step 3: 生成总结（依赖分析）
                        PipelineStep.builder()
                                .stepId("summary")
                                .agentId(AgentIds.SUMMARY)
                                .action("generate_summary")
                                .dependsOn(List.of("analyze"))
                                .resultMapping(Map.of("analyze", "content"))
                                .optional(true)
                                .build()
                ))
                .build();
    }

    // ========== 新增：单步 Agent Pipeline 模板 ==========

    /** 资源生成 */
    public static PipelineConfig resource() {
        return PipelineConfig.builder()
                .pipelineId("resource")
                .name("资源生成")
                .description("生成学习资源（讲义、练习题、代码示例等）")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("resource")
                                .agentId(AgentIds.RESOURCE)
                                .action("generate_lesson")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 图表生成 */
    public static PipelineConfig diagram() {
        return PipelineConfig.builder()
                .pipelineId("diagram")
                .name("图表生成")
                .description("生成图表、思维导图、流程图等")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("diagram")
                                .agentId(AgentIds.DIAGRAM)
                                .action("generate_diagram")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 总结生成 */
    public static PipelineConfig summary() {
        return PipelineConfig.builder()
                .pipelineId("summary")
                .name("总结生成")
                .description("总结知识点、生成复习摘要")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("summary")
                                .agentId(AgentIds.SUMMARY)
                                .action("generate_summary")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 题目生成 */
    public static PipelineConfig quiz() {
        return PipelineConfig.builder()
                .pipelineId("quiz")
                .name("题目生成")
                .description("按要求或基于知识库资料生成混合题型练习题")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("quiz")
                                .agentId(AgentIds.QUIZ)
                                .action("generate_quiz")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 用户画像 */
    public static PipelineConfig profile() {
        return PipelineConfig.builder()
                .pipelineId("profile")
                .name("用户画像")
                .description("获取用户学习画像、知识掌握度")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("profile")
                                .agentId(AgentIds.PROFILE)
                                .action("get_profile")
                                .timeoutMs(30000)
                                .build()
                ))
                .build();
    }

    /** 个性化推荐 */
    public static PipelineConfig recommendation() {
        return PipelineConfig.builder()
                .pipelineId("recommendation")
                .name("个性化推荐")
                .description("推荐学习资源、练习题")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("recommendation")
                                .agentId(AgentIds.RECOMMENDATION)
                                .action("recommend")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 学习评估 */
    public static PipelineConfig assessment() {
        return PipelineConfig.builder()
                .pipelineId("assessment")
                .name("学习评估")
                .description("评估答案质量、批改作业")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("assessment")
                                .agentId(AgentIds.ASSESSMENT)
                                .action("grade")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /** 学习干预 */
    public static PipelineConfig intervention() {
        return PipelineConfig.builder()
                .pipelineId("intervention")
                .name("学习干预")
                .description("学习状态评估与干预建议")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("intervention")
                                .agentId(AgentIds.INTERVENTION)
                                .action("assess_and_intervene")
                                .timeoutMs(60000)
                                .build()
                ))
                .build();
    }

    /**
     * 学习路径核心流水线（仅画像+路径，不生成资源）
     * 用于异步模式：快速返回路径结果，资源按需生成
     */
    public static PipelineConfig learningPathCore() {
        return PipelineConfig.builder()
                .pipelineId("learning_path_core")
                .name("学习路径规划")
                .description("根据用户画像生成个性化学习路径（不含资源生成）")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("profile")
                                .agentId(AgentIds.PROFILE)
                                .action("get_profile")
                                .build(),
                        PipelineStep.builder()
                                .stepId("path_gen")
                                .agentId(AgentIds.LEARNING_PATH)
                                .action("generate_path")
                                .dependsOn(List.of("profile"))
                                .resultMapping(Map.of("profile", "profile"))
                                .build(),
                        // Step 3: 效果评估（可选，依赖路径生成）
                        PipelineStep.builder()
                                .stepId("effect")
                                .agentId(AgentIds.EFFECT)
                                .action("evaluate")
                                .dependsOn(List.of("path_gen"))
                                .resultMapping(Map.of("path_gen", "path"))
                                .optional(true)
                                .build()
                ))
                .build();
    }

    /**
     * 答题评估流水线
     * 答题后触发效果评估 + 路径调整
     */
    public static PipelineConfig quizEvaluation() {
        return PipelineConfig.builder()
                .pipelineId("quiz_evaluation")
                .name("答题评估")
                .description("学生提交答案后进行效果评估与路径调整")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("effect")
                                .agentId(AgentIds.EFFECT)
                                .action("evaluate")
                                .build()
                ))
                .build();
    }

    /**
     * 阶段三新增：多模态讲解 Pipeline（5 步）
     * <p>
     * 适用于"用图片解释 X"、"用视频讲解 Y"等媒体讲解类请求。
     * 流程：内容分析 → QA 讲解 → Prompt 生成 → 媒体生成 → 质量检查
     * <p>
     * 作为 LLM TaskPlan 的参考样例，PlanningAgent v2 也会自主组合等价步骤
     */
    public static PipelineConfig multimodalExplanation() {
        return PipelineConfig.builder()
                .pipelineId("multimodal_explanation")
                .name("多模态讲解")
                .description("文本讲解 → 媒体生成 → 质量检查，用于图片/视频讲解任务")
                .steps(List.of(
                        // 1. 内容分析（提取知识点）
                        PipelineStep.builder()
                                .stepId("content_analysis")
                                .agentId(AgentIds.CONTENT_ANALYSIS)
                                .action("analyze")
                                .maxRetries(1)
                                .timeoutMs(60000)
                                .build(),
                        // 2. QA 生成讲解文本（依赖分析）
                        PipelineStep.builder()
                                .stepId("qa")
                                .agentId(AgentIds.QA)
                                .action("generate_answer")
                                .dependsOn(List.of("content_analysis"))
                                .resultMapping(Map.of("content_analysis", "content"))
                                .maxRetries(1)
                                .timeoutMs(60000)
                                .build(),
                        // 3. Prompt 生成（依赖讲解）
                        PipelineStep.builder()
                                .stepId("prompt_gen")
                                .agentId(AgentIds.PROMPT_GEN)
                                .action("generate_prompt")
                                .dependsOn(List.of("qa"))
                                .resultMapping(Map.of("qa", "explanation"))
                                .maxRetries(2)
                                .timeoutMs(30000)
                                .build(),
                        // 4. 媒体生成（依赖 prompt）
                        PipelineStep.builder()
                                .stepId("media_gen")
                                .agentId(AgentIds.MEDIA_GEN)
                                .action("generate_media")
                                .dependsOn(List.of("prompt_gen"))
                                .resultMapping(Map.of("prompt_gen", "prompt"))
                                .maxRetries(2)
                                .timeoutMs(120000)
                                .build(),
                        // 5. 质量检查（依赖媒体），失败不阻断
                        PipelineStep.builder()
                                .stepId("quality")
                                .agentId(AgentIds.QUALITY)
                                .action("check_media")
                                .dependsOn(List.of("media_gen"))
                                .resultMapping(Map.of("media_gen", "media"))
                                .optional(true)
                                .maxRetries(0)
                                .timeoutMs(30000)
                                .build()
                ))
                .totalTimeoutMs(360000)
                .parallel(false)
                .build();
    }

    /**
     * 阶段三新增：学习闭环 Pipeline（4 步）
     * <p>
     * 学生提交 quiz 答案后触发：批改 → 薄弱点 → 路径调整 → 资源推送
     */
    public static PipelineConfig learningLoop() {
        return PipelineConfig.builder()
                .pipelineId("learning_loop")
                .name("学习闭环")
                .description("学生提交 quiz 答案后：批改→薄弱点→路径调整→资源推送")
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("assessment")
                                .agentId(AgentIds.ASSESSMENT)
                                .action("grade_and_analyze")
                                .resultMapping(Map.of("quiz_submission", "answers"))
                                .maxRetries(1)
                                .timeoutMs(60000)
                                .build(),
                        PipelineStep.builder()
                                .stepId("effect")
                                .agentId(AgentIds.EFFECT)
                                .action("evaluate")
                                .dependsOn(List.of("assessment"))
                                .resultMapping(Map.of("assessment", "weakness"))
                                .maxRetries(1)
                                .timeoutMs(30000)
                                .build(),
                        PipelineStep.builder()
                                .stepId("path_adjust")
                                .agentId(AgentIds.LEARNING_PATH)
                                .action("adjust_path")
                                .dependsOn(List.of("effect"))
                                .resultMapping(Map.of("effect", "weakness_profile"))
                                .maxRetries(1)
                                .timeoutMs(60000)
                                .build(),
                        PipelineStep.builder()
                                .stepId("resource_push")
                                .agentId(AgentIds.RECOMMENDATION)
                                .action("recommend")
                                .dependsOn(List.of("path_adjust"))
                                .resultMapping(Map.of("path_adjust", "adjusted_path"))
                                .maxRetries(1)
                                .timeoutMs(30000)
                                .build()
                ))
                .totalTimeoutMs(240000)
                .parallel(false)
                .build();
    }
}
