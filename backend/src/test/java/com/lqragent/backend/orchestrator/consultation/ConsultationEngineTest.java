package com.lqragent.backend.orchestrator.consultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.consultation.repository.AgentConsultationLogRepository;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import static org.mockito.Mockito.mock;

class ConsultationEngineTest {

    private ConsultationEngine engine;
    private final List<String> roundRoles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        LearningPathDto dto = LearningPathDto.builder()
                .pathId(1L)
                .goal("学 Python")
                .planDescription("Python 入门路径")
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("语法").order(0).kpId("kp1").build(),
                        LearningPathDto.PathNode.builder().title("项目").order(1).kpId("kp2").build()))
                .build();

        Queue<PathReviewDecision> decisions = new LinkedList<>();
        decisions.add(PathReviewDecision.revise("偏难", "增加基础章节"));
        decisions.add(PathReviewDecision.approve("难度合适"));

        engine = new ConsultationEngine(
                enabledConfig(),
                new StubLearningPathService(dto),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new AgentRegistry(),
                        new StubPathReviewService(decisions),
                        new QuizReviewService()),
                noopLogService());
    }

    @Test
    void consult_runsTwoRoundsThenApproves() {
        TaskContext ctx = new TaskContext("t1", "1", "s1", "学 Python");
        ctx.put(ConsultationEngine.LISTENER_KEY, new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, List<String> participants, int maxRounds) {
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
                roundRoles.add(round + ":" + role);
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
            }
        });

        AgentResponse resp = engine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", java.util.Map.of("userId", "1")),
                ctx);

        assertTrue(resp.isSuccess());
        assertEquals(4, roundRoles.size());
        assertTrue(roundRoles.contains("1:draft"));
        assertTrue(roundRoles.contains("1:revise"));
        assertTrue(roundRoles.contains("2:draft"));
        assertTrue(roundRoles.contains("2:approve"));
        assertNotNull(ctx.getResult("path_consult"));
    }

    @Test
    void consult_maxRoundsExhaustedStillProducesPath() {
        Queue<PathReviewDecision> alwaysRevise = new LinkedList<>();
        alwaysRevise.add(PathReviewDecision.revise("偏难", "再简化"));
        alwaysRevise.add(PathReviewDecision.revise("仍偏难", "再简化"));

        ConsultationEngine maxRoundEngine = new ConsultationEngine(
                enabledConfig(),
                new StubLearningPathService(samplePath()),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new AgentRegistry(),
                        new StubPathReviewService(alwaysRevise),
                        new QuizReviewService()),
                noopLogService());

        TaskContext ctx = new TaskContext("t-max", "1", "s1", "学 Python");
        StopReason[] stop = new StopReason[1];
        ctx.put(ConsultationEngine.LISTENER_KEY, new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, List<String> participants, int maxRounds) {
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
                stop[0] = reason;
            }
        });

        AgentResponse resp = maxRoundEngine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", java.util.Map.of("userId", "1")),
                ctx);

        assertTrue(resp.isSuccess());
        assertEquals(StopReason.MAX_ROUNDS, stop[0]);
        assertNotNull(ctx.getResult("path_consult"));
    }

    @Test
    void consult_emitsProfileConstraintsOnFirstRound() {
        TaskContext ctx = new TaskContext("t-profile", "1", "s1", "学 Python");
        ctx.setResult("profile", java.util.Map.of("summary", "零基础，目标 Python 数据分析"));
        final List<String> roles = new ArrayList<>();
        ctx.put(ConsultationEngine.LISTENER_KEY, new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, List<String> participants, int maxRounds) {
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
                roles.add(round + ":" + role);
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
            }
        });

        engine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", java.util.Map.of("userId", "1")),
                ctx);

        assertTrue(roles.contains("1:constraints"));
    }

    @Test
    void consult_appliesRevisionAfterRevise() {
        LearningPathDto beginnerHeavy = LearningPathDto.builder()
                .pathId(99L)
                .goal("学 Python")
                .planDescription("入门路线")
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("Python简介与环境搭建").order(0).kpId("kp0").build(),
                        LearningPathDto.PathNode.builder().title("变量与数据类型").order(1).kpId("kp1").build(),
                        LearningPathDto.PathNode.builder().title("装饰器").order(2).kpId("kp2").build()))
                .build();

        Queue<PathReviewDecision> decisions = new LinkedList<>();
        decisions.add(PathReviewDecision.revise("偏易", "跳过基础入门，增加进阶内容"));
        decisions.add(PathReviewDecision.approve("合适"));

        ConsultationEngine reviseEngine = new ConsultationEngine(
                enabledConfig(),
                new StubLearningPathService(beginnerHeavy),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new AgentRegistry(),
                        new StubPathReviewService(decisions),
                        new QuizReviewService()),
                noopLogService());

        TaskContext ctx = new TaskContext("t-revise", "1", "s1", "学 Python\n补充信息：有一点基础");
        ctx.setResult("profile", Map.of("summary", "有一点基础"));

        AgentResponse resp = reviseEngine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", Map.of("userId", "1")),
                ctx);

        assertTrue(resp.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> step = (Map<String, Object>) ctx.getResult("path_consult");
        assertNotNull(step);
        assertEquals(1, step.get("nodeCount"));
    }

    @Test
    void consult_timeout_stopsWithTimeout() {
        AppRuntimeConfig shortTimeout = new AppRuntimeConfig(null, null) {
            @Override
            public boolean isConsultationEnabled() {
                return true;
            }

            @Override
            public boolean isConsultationSceneEnabled(String scene) {
                return true;
            }

            @Override
            public int getConsultationMaxRounds() {
                return 5;
            }

            @Override
            public long getConsultationTimeoutMs() {
                return 10L;
            }

            @Override
            public boolean isConsultationStreamTranscript() {
                return false;
            }
        };

        ConsultationEngine timeoutEngine = new ConsultationEngine(
                shortTimeout,
                new StubLearningPathService(samplePath()),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new AgentRegistry(),
                        new SlowReviseReviewService(),
                        new QuizReviewService()),
                noopLogService());

        StopReason[] stop = new StopReason[1];
        TaskContext ctx = new TaskContext("t-timeout", "1", "s1", "学 Python");
        ctx.put(ConsultationEngine.LISTENER_KEY, new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, List<String> participants, int maxRounds) {
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
                stop[0] = reason;
            }
        });

        timeoutEngine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", Map.of()),
                ctx);

        assertEquals(StopReason.TIMEOUT, stop[0]);
    }

    private static AppRuntimeConfig enabledConfig() {
        return new AppRuntimeConfig(null, null) {
            @Override
            public boolean isConsultationEnabled() {
                return true;
            }

            @Override
            public boolean isConsultationSceneEnabled(String scene) {
                return true;
            }

            @Override
            public int getConsultationMaxRounds() {
                return 2;
            }

            @Override
            public long getConsultationTimeoutMs() {
                return 90_000L;
            }

            @Override
            public boolean isConsultationStreamTranscript() {
                return false;
            }
        };
    }

    private static LearningPathDto samplePath() {
        return LearningPathDto.builder()
                .pathId(1L)
                .goal("学 Python")
                .planDescription("Python 入门路径")
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("语法").order(0).kpId("kp1").build(),
                        LearningPathDto.PathNode.builder().title("项目").order(1).kpId("kp2").build()))
                .build();
    }

    @Test
    void consult_disabled_fallsBackToSingleGenerate() {
        AppRuntimeConfig off = new AppRuntimeConfig(null, null) {
            @Override
            public boolean isConsultationEnabled() {
                return false;
            }
        };
        LearningPathDto dto = LearningPathDto.builder()
                .goal("学 Python")
                .planDescription("单步路径")
                .nodes(List.of(LearningPathDto.PathNode.builder().title("语法").order(0).kpId("kp1").build()))
                .build();
        ConsultationEngine disabledEngine = new ConsultationEngine(
                off,
                new StubLearningPathService(dto),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new AgentRegistry(),
                        new StubPathReviewService(new LinkedList<>()),
                        new QuizReviewService()),
                noopLogService());

        AgentResponse resp = disabledEngine.consultAsAgentResponse(
                new AgentRequest("consult_path", "学 Python", java.util.Map.of()),
                new TaskContext("t2", "1", null, "学 Python"));

        assertTrue(resp.isSuccess());
        assertEquals(0, roundRoles.size());
    }

    private static class StubLearningPathService extends LearningPathService {
        private final LearningPathDto fixed;

        StubLearningPathService(LearningPathDto fixed) {
            super(null, null, null, null, null, null);
            this.fixed = fixed;
        }

        @Override
        public LearningPathDto generatePath(Long userId, String goal, String currentKpId) {
            return fixed;
        }

        @Override
        public LearningPathDto applyConsultationRevision(
                LearningPathDto path, String profileSummary, String goal, String feedback) {
            return PathConsultationAdjuster.apply(path, profileSummary, goal, feedback);
        }
    }

    private static class SlowReviseReviewService extends PathReviewService {
        SlowReviseReviewService() {
            super(null, null);
        }

        @Override
        public PathReviewDecision review(String profileSummary, LearningPathDto path, String goal) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return PathReviewDecision.revise("偏难", "简化");
        }
    }

    private static class StubPathReviewService extends PathReviewService {
        private final Queue<PathReviewDecision> decisions;

        StubPathReviewService(Queue<PathReviewDecision> decisions) {
            super(null, null);
            this.decisions = decisions;
        }

        @Override
        public PathReviewDecision review(String profileSummary, LearningPathDto path, String goal) {
            PathReviewDecision next = decisions.poll();
            return next != null ? next : PathReviewDecision.approve("默认通过");
        }
    }

    private static class StubLearnerProfileService extends LearnerProfileService {
        StubLearnerProfileService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public String getProfileSummary(Long userId) {
            return "";
        }
    }

    private static ConsultationLogService noopLogService() {
        AppRuntimeConfig cfg = new AppRuntimeConfig(null, null) {
            @Override
            public boolean isSupervisorPersistTranscript() {
                return false;
            }

            @Override
            public String get(String key) {
                return "";
            }

            @Override
            public String get(String key, String defaultValue) {
                return defaultValue;
            }
        };
        return new ConsultationLogService(cfg, mock(AgentConsultationLogRepository.class));
    }
}
