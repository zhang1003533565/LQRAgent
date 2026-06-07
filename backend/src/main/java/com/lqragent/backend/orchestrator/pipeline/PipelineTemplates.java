package com.lqragent.backend.orchestrator.pipeline;

import com.lqragent.backend.orchestrator.AgentIds;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
        log.info("[PipelineTemplates] registered {} templates", 4);
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
}
