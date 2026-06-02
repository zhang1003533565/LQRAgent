package com.lqragent.backend.agents.learnerprofile;

import com.lqragent.backend.core.agent.Agent;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.core.tool.ToolRegistry;
import com.lqragent.backend.core.tool.ToolSchema;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LearnerProfileAgent implements Agent {

    private final LearnerProfileService learnerProfileService;

    @Override
    public String agentId() { return AgentIds.LEARNER_PROFILE; }

    @Override
    public AgentResult process(AgentTask task) {
        Long userId = task.getUserId() != null ? task.getUserId() : RequestContext.getUserId();
        String sessionId = task.getSessionId();
        ProfileSummaryDto summary = sessionId != null && !sessionId.isBlank()
                ? learnerProfileService.extractFromSession(userId, sessionId)
                : learnerProfileService.getSummary(userId);
        return AgentResult.builder()
                .success(true)
                .data(summary != null ? Map.of("summary", summary) : Map.of())
                .build();
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return """
            你是学生画像分析专家。根据对话上下文自动、增量抽取 6 个维度：
            知识基础、学习目标、认知风格、易错点偏好、学习节奏、兴趣方向。
            同时识别学生已掌握与待学习的具体知识点（mastered_topics / pending_topics）。
            调用 extract_profile 完成抽取；get_profile 仅查询当前画像。
            """;
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("get_profile", "查询学生当前画像",
                ToolSchema.params(Map.of("userId", ToolSchema.stringParam("学生ID", "")), "userId")),
            ToolSchema.of("extract_profile", "从对话记录增量抽取6维度画像",
                ToolSchema.params(Map.of(
                    "sessionId", ToolSchema.stringParam("会话ID", ""),
                    "dialogues", Map.of("type", "array", "items", Map.of("type", "string"), "description", "可选对话文本列表")
                ), "sessionId"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "get_profile", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            Object rawUid = p.getOrDefault("userId", RequestContext.getUserId());
            Long uid = rawUid instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(rawUid));
            if (uid == 0L) uid = RequestContext.getUserId();
            return learnerProfileService.getSummary(uid);
        });

        registry.register(agentId(), "extract_profile", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            Long userId = RequestContext.getUserId();
            String sessionId = (String) p.get("sessionId");

            ProfileSummaryDto dto;
            if (sessionId != null && !sessionId.isBlank()) {
                dto = learnerProfileService.extractFromSession(userId, sessionId);
            } else {
                @SuppressWarnings("unchecked")
                List<String> dialogues = (List<String>) p.get("dialogues");
                if (dialogues == null || dialogues.isEmpty()) {
                    return Map.of("status", "error", "message", "缺少 sessionId 或 dialogues");
                }
                dto = learnerProfileService.extractFromDialogues(userId, dialogues);
            }

            return Map.of(
                "status", "success",
                "message", "画像已从对话增量更新",
                "profileId", dto.getId() != null ? dto.getId() : 0,
                "knowledgeLevel", dto.getKnowledgeLevel() != null ? dto.getKnowledgeLevel() : "",
                "topicMastery", dto.getTopicMastery() != null ? dto.getTopicMastery() : "{}",
                "next_step_hint", "可调用 get_profile 查看完整摘要"
            );
        });
    }
}
