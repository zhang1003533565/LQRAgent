package com.lqragent.backend.orchestrator.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.learnerprofile.service.ProfileMergeService;
import com.lqragent.backend.chat.service.UserMemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聚合学习者画像、掌握度、长期记忆，供 PlanningAgent 与 Pipeline 使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerContextService {

    private static final int MEMORY_PROMPT_LIMIT = 5;

    private final LearnerProfileService learnerProfileService;
    private final ProfileMergeService profileMergeService;
    private final UserMemoryService userMemoryService;

    public LearnerContextDto buildForUser(Long userId) {
        if (userId == null) {
            return LearnerContextDto.builder().userId(null).topicMastery(Map.of()).build();
        }

        String profileSummary = safeProfileSummary(userId);
        String memorySummary = safeMemorySummary(userId);
        Map<String, String> mastery = safeTopicMastery(userId);
        String promptBlock = buildPromptBlock(profileSummary, memorySummary, mastery);

        return LearnerContextDto.builder()
                .userId(userId)
                .profileSummary(profileSummary)
                .memorySummary(memorySummary)
                .topicMastery(mastery)
                .promptBlock(promptBlock)
                .build();
    }

    /** 注入 TaskContext，供 Pipeline 各 Agent 读取 */
    public void enrichTaskContext(TaskContext context, Long userId) {
        if (context == null || userId == null) {
            return;
        }
        LearnerContextDto dto = buildForUser(userId);
        if (dto.isEmpty()) {
            return;
        }
        context.put("learnerProfile", dto.getProfileSummary());
        context.put("learnerMemory", dto.getMemorySummary());
        context.put("topicMastery", dto.getTopicMastery());
        context.put("learnerContext", dto.getPromptBlock());
    }

    public String buildPromptBlock(Long userId) {
        return buildForUser(userId).getPromptBlock();
    }

    private String safeProfileSummary(Long userId) {
        try {
            return learnerProfileService.getProfileSummary(userId);
        } catch (Exception e) {
            log.warn("[LearnerContext] profile summary failed: {}", e.getMessage());
            return "";
        }
    }

    private String safeMemorySummary(Long userId) {
        try {
            return userMemoryService.getImportantMemoriesForPrompt(userId, MEMORY_PROMPT_LIMIT);
        } catch (Exception e) {
            log.warn("[LearnerContext] memory summary failed: {}", e.getMessage());
            return "";
        }
    }

    private Map<String, String> safeTopicMastery(Long userId) {
        try {
            var profile = learnerProfileService.getOrCreate(userId);
            return profileMergeService.readTopicMastery(profile.getTopicMastery());
        } catch (Exception e) {
            log.warn("[LearnerContext] topic mastery failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private String buildPromptBlock(String profileSummary, String memorySummary, Map<String, String> mastery) {
        StringBuilder sb = new StringBuilder();
        if (profileSummary != null && !profileSummary.isBlank()) {
            sb.append("学习者画像：").append(profileSummary.trim()).append("\n");
        }
        if (mastery != null && !mastery.isEmpty()) {
            String masteryLine = mastery.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            sb.append("知识点掌握：").append(masteryLine).append("\n");
        }
        if (memorySummary != null && !memorySummary.isBlank()) {
            sb.append(memorySummary.trim());
            if (!memorySummary.endsWith("\n")) {
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }
}
