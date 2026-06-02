package com.lqragent.backend.agents.learner_profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.handler.WebSocketSessionManager;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.agents.learner_profile.dto.ProfileDetailDto;
import com.lqragent.backend.agents.learner_profile.dto.ProfilePatchRequest;
import com.lqragent.backend.agents.learner_profile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learner_profile.entity.LearnerProfile;
import com.lqragent.backend.agents.learner_profile.repository.LearnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 学生画像服务。
 * 6 维度：知识水平/认知风格/学习节奏/常见错误/兴趣方向/偏好资源 + 知识点掌握 topicMastery。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerProfileService {

    private static final int EXTRACT_MESSAGE_LIMIT = 20;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final LearnerProfileRepository profileRepo;
    private final WebSocketSessionManager sessionManager;
    private final ProfileExtractor profileExtractor;
    private final ProfileMergeService profileMergeService;
    private final ChatMessageRepository chatMessageRepository;

    /** 获取或创建画像 */
    @Transactional
    public LearnerProfile getOrCreate(Long userId) {
        return profileRepo.findByUserId(userId)
                .orElseGet(() -> {
                    LearnerProfile profile = LearnerProfile.builder()
                            .userId(userId)
                            .knowledgeLevel("BEGINNER")
                            .learningPace("NORMAL")
                            .build();
                    LearnerProfile saved = profileRepo.save(profile);
                    log.info("[LearnerProfile] 创建画像: userId={}, id={}", userId, saved.getId());
                    return saved;
                });
    }

    /** 获取画像摘要（可能自动创建新画像，因此不可用 readOnly） */
    @Transactional
    public ProfileSummaryDto getSummary(Long userId) {
        LearnerProfile profile = getOrCreate(userId);
        return toDto(profile);
    }

    @Transactional(readOnly = true)
    public ProfileDetailDto getDetail(Long userId) {
        LearnerProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile == null) {
            return ProfileDetailDto.builder()
                    .summary(ProfileSummaryDto.builder().userId(userId).knowledgeLevel("BEGINNER").build())
                    .weakTopics(List.of())
                    .recentGoals(List.of())
                    .knowledgeMap(List.of())
                    .build();
        }

        Map<String, String> topics = profileMergeService.readTopicMastery(profile.getTopicMastery());
        List<String> weakTopics = parseWeakTopics(profile.getCommonErrors());
        List<String> goals = profile.getLearningGoal() != null && !profile.getLearningGoal().isBlank()
                ? List.of(profile.getLearningGoal())
                : List.of();

        return ProfileDetailDto.builder()
                .summary(toDto(profile))
                .weakTopics(weakTopics)
                .recentGoals(goals)
                .knowledgeMap(ProfileDetailDto.fromTopicMastery(topics))
                .build();
    }

    /** 局部更新画像 */
    @Transactional
    public ProfileSummaryDto patch(Long userId, ProfilePatchRequest request) {
        LearnerProfile profile = getOrCreate(userId);

        if (request.getKnowledgeLevel() != null) profile.setKnowledgeLevel(request.getKnowledgeLevel());
        if (request.getLearningGoal() != null) profile.setLearningGoal(request.getLearningGoal());
        if (request.getCognitiveStyle() != null) profile.setCognitiveStyle(request.getCognitiveStyle());
        if (request.getCommonErrors() != null) profile.setCommonErrors(request.getCommonErrors());
        if (request.getLearningPace() != null) profile.setLearningPace(request.getLearningPace());
        if (request.getInterestDirection() != null) profile.setInterestDirection(request.getInterestDirection());
        if (request.getPreferredResourceType() != null) profile.setPreferredResourceType(request.getPreferredResourceType());
        if (request.getTopicMastery() != null) profile.setTopicMastery(request.getTopicMastery());

        LearnerProfile saved = profileRepo.save(profile);
        log.info("[LearnerProfile] 更新画像: userId={}", userId);
        fireProfilePatch(userId, saved);
        return toDto(saved);
    }

    /**
     * 从会话最近对话中增量抽取画像（LLM + 规则兜底），并 WS 推送 profile_patch。
     */
    @Transactional
    public ProfileSummaryDto extractFromSession(Long userId, String sessionId) {
        LearnerProfile profile = getOrCreate(userId);

        List<ChatMessage> recent = chatMessageRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(0, EXTRACT_MESSAGE_LIMIT));
        if (recent.isEmpty()) {
            return toDto(profile);
        }
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        for (ChatMessage m : chronological) {
            if (m.getSender() == ChatMessage.Sender.USER) {
                profileMergeService.mergeFromDialogueRules(profile, m.getBody());
            }
        }

        String llmJson = profileExtractor.extract(chronological);
        if (llmJson != null) {
            profileMergeService.mergeFromLlmJson(profile, llmJson);
        }

        LearnerProfile saved = profileRepo.save(profile);
        log.info("[LearnerProfile] 对话画像抽取完成: userId={}, sessionId={}", userId, sessionId);
        fireProfilePatch(userId, saved);
        return toDto(saved);
    }

    /** 从对话文本列表增量抽取（Tool 传入 dialogues 时使用） */
    @Transactional
    public ProfileSummaryDto extractFromDialogues(Long userId, List<String> dialogues) {
        LearnerProfile profile = getOrCreate(userId);
        for (String line : dialogues) {
            profileMergeService.mergeFromDialogueRules(profile, line);
        }
        List<ChatMessage> pseudo = new ArrayList<>();
        for (String line : dialogues) {
            pseudo.add(ChatMessage.builder().sender(ChatMessage.Sender.USER).body(line).build());
        }
        String llmJson = profileExtractor.extract(pseudo);
        if (llmJson != null) {
            profileMergeService.mergeFromLlmJson(profile, llmJson);
        }
        LearnerProfile saved = profileRepo.save(profile);
        fireProfilePatch(userId, saved);
        return toDto(saved);
    }

    /** 答题后自动更新画像 */
    @Transactional
    public void updateAfterQuiz(Long userId, boolean correct, int score, String kpId) {
        LearnerProfile profile = getOrCreate(userId);

        String currentLevel = profile.getKnowledgeLevel();
        if ("BEGINNER".equals(currentLevel) && score >= 80) {
            profile.setKnowledgeLevel("INTERMEDIATE");
        } else if ("INTERMEDIATE".equals(currentLevel) && score >= 90) {
            profile.setKnowledgeLevel("ADVANCED");
        }

        if (!correct && score < 60) {
            String existingErrors = profile.getCommonErrors();
            String newError = "{\"kpId\":\"" + kpId + "\",\"score\":" + score + "}";
            if (existingErrors == null || existingErrors.isBlank()) {
                profile.setCommonErrors("[" + newError + "]");
            } else {
                profile.setCommonErrors(existingErrors.replace("]", ", " + newError + "]"));
            }
            Map<String, String> mastery = profileMergeService.readTopicMastery(profile.getTopicMastery());
            mastery.put(kpId, "PENDING");
            try {
                profile.setTopicMastery(JSON.writeValueAsString(mastery));
            } catch (Exception ignored) {
            }
        } else if (correct && score >= 80) {
            Map<String, String> mastery = profileMergeService.readTopicMastery(profile.getTopicMastery());
            mastery.put(kpId, "MASTERED");
            try {
                profile.setTopicMastery(JSON.writeValueAsString(mastery));
            } catch (Exception ignored) {
            }
        }

        LearnerProfile saved = profileRepo.save(profile);
        log.info("[LearnerProfile] 答题后更新: userId={}, correct={}, score={}", userId, correct, score);
        fireProfilePatch(userId, saved);
    }

    /** 推 profile_patch 事件到前端 */
    private void fireProfilePatch(Long userId, LearnerProfile profile) {
        try {
            var payload = JSON.valueToTree(toDto(profile));
            sessionManager.sendToUser(userId, "profile_patch", payload);
        } catch (Exception e) {
            log.warn("[LearnerProfile] WS推送失败: userId={}", userId, e);
        }
    }

    private ProfileSummaryDto toDto(LearnerProfile p) {
        return ProfileSummaryDto.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .knowledgeLevel(p.getKnowledgeLevel())
                .learningGoal(p.getLearningGoal())
                .cognitiveStyle(p.getCognitiveStyle())
                .commonErrors(p.getCommonErrors())
                .learningPace(p.getLearningPace())
                .interestDirection(p.getInterestDirection())
                .preferredResourceType(p.getPreferredResourceType())
                .topicMastery(p.getTopicMastery())
                .build();
    }

    private List<String> parseWeakTopics(String commonErrors) {
        if (commonErrors == null || commonErrors.isBlank()) return List.of();
        try {
            return JSON.readValue(commonErrors, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of(commonErrors);
        }
    }
}
