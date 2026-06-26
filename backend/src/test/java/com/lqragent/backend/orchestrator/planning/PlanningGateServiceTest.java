package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lqragent.backend.orchestrator.context.LearnerContextDto;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

class PlanningGateServiceTest {

    private PlanningGateService gate;

    @BeforeEach
    void setUp() {
        AppRuntimeConfig config = new AppRuntimeConfig(null, null) {
            @Override
            public boolean isPlanningGateEnabled() {
                return true;
            }

            @Override
            public boolean isPlanningGatePreferCorePath() {
                return true;
            }
        };
        gate = new PlanningGateService(config);
    }

    @Test
    void g1_emptyContext_vagueLearning_returnsClarify() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder().build();

        PlanResult result = gate.apply(plan, "我想学 Python", ctx, false);

        assertTrue(result.isClarify());
        assertTrue(result.clarifyQuestions() != null && !result.clarifyQuestions().isEmpty());
    }

    @Test
    void g3_sufficientMessage_returnsLearningPathCore() {
        PlanResult plan = PlanResult.pipeline(
                PipelineTemplates.learningPath(), PipelineTemplates.learningPath().getSteps());
        LearnerContextDto ctx = LearnerContextDto.builder().build();

        PlanResult result = gate.apply(
                plan, "我想学数据分析，零基础，计划3个月", ctx, false);

        assertTrue(result.isPipeline());
        assertEquals("learning_path_core", result.pipelineConfig().getPipelineId());
    }

    @Test
    void g4_explicitFullPlan_keepsFullLearningPath() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder().build();

        PlanResult result = gate.apply(plan, "帮我生成完整的学习计划和讲义", ctx, false);

        assertTrue(result.isPipeline());
        assertEquals("learning_path", result.pipelineConfig().getPipelineId());
    }

    @Test
    void g5_obviousQa_notIntervened() {
        PlanResult plan = PlanResult.simple(PlanIntent.QA);
        LearnerContextDto ctx = LearnerContextDto.builder().build();

        PlanResult result = gate.apply(plan, "什么是闭包？", ctx, false);

        assertTrue(result.isSimple());
        assertEquals(PlanIntent.QA, result.intent());
    }

    @Test
    void skipG1_afterClarifyMerge_usesG3WhenDetailsPresent() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder().build();

        PlanResult result = gate.apply(
                plan, "我想学 Python\n补充信息：零基础，3个月", ctx, true);

        assertTrue(result.isPipeline());
        assertEquals("learning_path_core", result.pipelineConfig().getPipelineId());
    }

    @Test
    void g2_profileConflictsWithMessage_usesTopicClarifyNotProfileDump() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder()
                .profileSummary("知识水平: INTERMEDIATE; 学习目标: 理解装饰器模式的结构与应用; 认知风格: visual")
                .build();

        PlanResult result = gate.apply(plan, "我想学习python", ctx, false);

        assertTrue(result.isClarify());
        String joined = String.join(" ", result.clarifyQuestions());
        assertTrue(joined.contains("Python"), joined);
        assertTrue(!joined.contains("装饰器"), joined);
        assertTrue(!joined.contains("根据你的画像"), joined);
        assertTrue(!joined.contains("画像"), joined);
    }

    @Test
    void g2_profileAligned_usesNaturalContinuationNotProfileDump() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder()
                .profileSummary("知识水平: INTERMEDIATE; 学习目标: 理解装饰器模式; 认知风格: visual")
                .build();

        PlanResult result = gate.apply(plan, "帮我学一下，想继续深入", ctx, false);

        assertTrue(result.isClarify());
        String joined = String.join(" ", result.clarifyQuestions());
        assertTrue(joined.contains("基础") || joined.contains("时间") || joined.contains("成果")
                || joined.contains("之前提到过") || joined.contains("重点"), joined);
        assertTrue(!joined.contains("根据你的画像"), joined);
        assertTrue(!joined.contains("INTERMEDIATE"), joined);
    }

    @Test
    void skipG1_topicAndYiDianJiChu_proceedsToCore() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder()
                .profileSummary("知识水平: BEGINNER")
                .build();

        PlanResult result = gate.apply(
                plan, "我想学python\n补充信息：有一点基础", ctx, true);

        assertTrue(result.isPipeline());
        assertEquals("learning_path_core", result.pipelineConfig().getPipelineId());
    }

    @Test
    void g2_javascriptTopic_notMislabeledAsJava() {
        PlanResult plan = PlanResult.simple(PlanIntent.LEARNING_PATH);
        LearnerContextDto ctx = LearnerContextDto.builder()
                .profileSummary("知识水平: BEGINNER")
                .build();

        PlanResult result = gate.apply(plan, "我想学 javascript", ctx, false);

        assertTrue(result.isClarify());
        String joined = String.join(" ", result.clarifyQuestions());
        assertTrue(joined.contains("JavaScript"), joined);
        assertTrue(!joined.contains("学 Java 的话"), joined);
    }

    @Test
    void gateDisabled_passesThrough() {
        AppRuntimeConfig off = new AppRuntimeConfig(null, null) {
            @Override
            public boolean isPlanningGateEnabled() {
                return false;
            }
        };
        PlanningGateService disabledGate = new PlanningGateService(off);
        PlanResult plan = PlanResult.simple(PlanIntent.QA);

        PlanResult result = disabledGate.apply(plan, "我想学 Python", LearnerContextDto.builder().build(), false);

        assertTrue(result.isSimple());
    }

    @Test
    void quizGenerationIntent_normalizesDynamicQuizPlan() {
        PlanResult dynamic = PlanResult.pipeline(buildDynamicQuizPlan(), buildDynamicQuizPlan().getSteps());

        PlanResult result = gate.apply(dynamic, "出 5 道闭包练习题", LearnerContextDto.builder().build(), false);

        assertTrue(result.isPipeline());
        assertEquals("quiz", result.pipelineConfig().getPipelineId());
        assertTrue(result.pipelineConfig().getSteps().stream()
                .anyMatch(s -> "quiz_consult".equals(s.getStepId())));
        assertTrue(result.pipelineConfig().getSteps().stream()
                .anyMatch(s -> "consult_quiz".equals(s.getAction())));
    }

    private static com.lqragent.backend.orchestrator.pipeline.PipelineConfig buildDynamicQuizPlan() {
        return com.lqragent.backend.orchestrator.pipeline.PipelineConfig.builder()
                .pipelineId("dynamic_plan-test")
                .name("动态出题")
                .steps(java.util.List.of(
                        com.lqragent.backend.orchestrator.pipeline.PipelineStep.builder()
                                .stepId("s1")
                                .agentId("quiz_agent")
                                .action("generate")
                                .build()))
                .build();
    }
}
