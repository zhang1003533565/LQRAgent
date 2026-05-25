package com.lqragent.backend.learnerprofile.service;

import com.lqragent.backend.chat.handler.WebSocketSessionManager;
import com.lqragent.backend.learnerprofile.dto.ProfilePatchRequest;
import com.lqragent.backend.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.learnerprofile.entity.LearnerProfile;
import com.lqragent.backend.learnerprofile.repository.LearnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生画像服务。
 * 6 维度：知识水平/认知风格/学习节奏/常见错误/兴趣方向/偏好资源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearnerProfileService {

    private final LearnerProfileRepository profileRepo;
    private final WebSocketSessionManager sessionManager;

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

        LearnerProfile saved = profileRepo.save(profile);
        log.info("[LearnerProfile] 更新画像: userId={}", userId);
        fireProfilePatch(userId, saved);
        return toDto(saved);
    }

    /** 答题后自动更新画像 */
    @Transactional
    public void updateAfterQuiz(Long userId, boolean correct, int score, String kpId) {
        LearnerProfile profile = getOrCreate(userId);

        // 更新 knowledgeLevel
        String currentLevel = profile.getKnowledgeLevel();
        if ("BEGINNER".equals(currentLevel) && score >= 80) {
            profile.setKnowledgeLevel("INTERMEDIATE");
        } else if ("INTERMEDIATE".equals(currentLevel) && score >= 90) {
            profile.setKnowledgeLevel("ADVANCED");
        }

        // 记录错误
        if (!correct && score < 60) {
            String existingErrors = profile.getCommonErrors();
            String newError = "{\"kpId\":\"" + kpId + "\",\"score\":" + score + "}";
            if (existingErrors == null || existingErrors.isBlank()) {
                profile.setCommonErrors("[" + newError + "]");
            } else {
                // 简单追加
                profile.setCommonErrors(existingErrors.replace("]", ", " + newError + "]"));
            }
        }

        LearnerProfile saved = profileRepo.save(profile);
        log.info("[LearnerProfile] 答题后更新: userId={}, correct={}, score={}", userId, correct, score);
        fireProfilePatch(userId, saved);
    }

    /** 推 profile_patch 事件到前端 */
    private void fireProfilePatch(Long userId, LearnerProfile profile) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var payload = mapper.valueToTree(toDto(profile));
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
                .build();
    }
}
