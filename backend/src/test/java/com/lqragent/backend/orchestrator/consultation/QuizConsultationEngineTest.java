package com.lqragent.backend.orchestrator.consultation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.quiz.tools.GenerateQuizTool;
import com.lqragent.backend.orchestrator.consultation.repository.AgentConsultationLogRepository;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.service.SysConfigService;

class QuizConsultationEngineTest {

    @Test
    void fallbackWhenConsultationDisabled() {
        QuizConsultationEngine engine = new QuizConsultationEngine(
                disabledConfig(),
                stubQuizTool(),
                new StubLearnerProfileService(),
                new ConsultationAgentInvoker(
                        new com.lqragent.backend.agents.base.AgentRegistry(),
                        new PathReviewService(null, null),
                        new QuizReviewService()),
                noopLogService());

        TaskContext ctx = new TaskContext("t1", "1", "s1", "出 Python 变量题");
        AgentResponse response = engine.consultAsAgentResponse(
                new AgentRequest("consult_quiz", "出 Python 变量题", Map.of("userId", "1")),
                ctx);

        assertTrue(response.isSuccess());
        assertNotNull(ctx.getResult("quiz_consult"));
    }

    private static GenerateQuizTool stubQuizTool() {
        return new GenerateQuizTool(null) {
            @Override
            public ToolResult execute(Map<String, Object> args) {
                Map<String, Object> quiz = Map.of(
                        "title", "变量练习",
                        "topic", String.valueOf(args.getOrDefault("topic", "变量")),
                        "difficulty", String.valueOf(args.getOrDefault("difficulty", "中等")),
                        "questions", List.of(
                                Map.of("id", 1, "stem", "Q1"),
                                Map.of("id", 2, "stem", "Q2"),
                                Map.of("id", 3, "stem", "Q3"),
                                Map.of("id", 4, "stem", "Q4"),
                                Map.of("id", 5, "stem", "Q5")));
                return ToolResult.success(AgentResponse.withData(
                        "quiz", "变量练习", "ok", "{}", quiz).toJson());
            }
        };
    }

    private static AppRuntimeConfig disabledConfig() {
        return new AppRuntimeConfig(emptySysConfig(), new MockEnvironment()) {
            @Override
            public boolean isConsultationEnabled() {
                return false;
            }
        };
    }

    private static ConsultationLogService noopLogService() {
        AppRuntimeConfig cfg = new AppRuntimeConfig(emptySysConfig(), new MockEnvironment()) {
            @Override
            public boolean isSupervisorPersistTranscript() {
                return false;
            }
        };
        return new ConsultationLogService(cfg, mock(AgentConsultationLogRepository.class));
    }

    private static SysConfigService emptySysConfig() {
        return new SysConfigService(null) {
            @Override
            public java.util.Optional<String> getValue(String key) {
                return java.util.Optional.empty();
            }
        };
    }

    private static class StubLearnerProfileService extends LearnerProfileService {
        StubLearnerProfileService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public String getProfileSummary(Long userId) {
            return "初学者";
        }
    }
}
