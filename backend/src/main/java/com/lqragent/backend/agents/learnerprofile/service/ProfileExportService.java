package com.lqragent.backend.agents.learnerprofile.service;

import com.lqragent.backend.agents.learnerprofile.dto.LearningAchievementDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileDetailDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileExportDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileTrendPointDto;
import com.lqragent.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileExportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final LearnerProfileService profileService;
    private final ProfileAnalyticsService profileAnalyticsService;

    @Transactional(readOnly = true)
    public ProfileExportDto export(Long userId, String format) {
        String fmt = format == null || format.isBlank() ? "markdown" : format.trim().toLowerCase();
        if ("pdf".equals(fmt)) {
            throw BusinessException.of("PDF 导出即将支持，请使用 format=markdown");
        }
        if (!"markdown".equals(fmt)) {
            throw BusinessException.of("不支持的导出格式：" + format);
        }

        ProfileDetailDto detail = profileService.getDetail(userId);
        List<ProfileTrendPointDto> trends = profileAnalyticsService.getTrends(userId, "30d", "mastery");
        List<LearningAchievementDto> achievements = profileAnalyticsService.getAchievements(userId);

        String content = buildMarkdown(detail, trends, achievements);
        String fileName = "learning-profile-" + LocalDate.now().format(FILE_DATE) + ".md";

        return ProfileExportDto.builder()
                .format("markdown")
                .content(content)
                .fileName(fileName)
                .build();
    }

    private String buildMarkdown(
            ProfileDetailDto detail,
            List<ProfileTrendPointDto> trends,
            List<LearningAchievementDto> achievements) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 学习画像报告\n\n");
        sb.append("生成日期：").append(LocalDate.now()).append("\n\n");

        int mastery = averageMastery(detail);
        sb.append("## 概览\n\n");
        sb.append("- 综合掌握度：").append(mastery).append("%\n");
        sb.append("- 连续学习：").append(detail.getStreakDays()).append(" 天\n");
        if (detail.getLearningGoal() != null && !detail.getLearningGoal().isBlank()) {
            sb.append("- 学习目标：").append(detail.getLearningGoal()).append("\n");
        }
        if (detail.getKnowledgeLevel() != null) {
            sb.append("- 知识水平：").append(detail.getKnowledgeLevel()).append("\n");
        }
        sb.append("- 已掌握知识点：").append(detail.getCompletedKpCount()).append(" 个\n\n");

        if (detail.getKnowledgeMap() != null && !detail.getKnowledgeMap().isEmpty()) {
            sb.append("## 知识点掌握\n\n");
            for (ProfileDetailDto.KnowledgeMapItem item : detail.getKnowledgeMap()) {
                String title = item.getTitle() != null ? item.getTitle() : item.getKpId();
                sb.append("- ").append(title)
                        .append("：").append(item.getMastery()).append("%（")
                        .append(item.getStatus()).append("）\n");
            }
            sb.append("\n");
        }

        if (detail.getWeakTopics() != null && !detail.getWeakTopics().isEmpty()) {
            sb.append("## 薄弱知识点\n\n");
            for (String topic : detail.getWeakTopics()) {
                sb.append("- ").append(topic).append("\n");
            }
            sb.append("\n");
        }

        if (!trends.isEmpty()) {
            sb.append("## 近 30 天趋势\n\n");
            for (ProfileTrendPointDto point : trends) {
                sb.append("- ").append(point.getDate());
                if (point.getOverallMasteryRate() != null) {
                    sb.append(" · 掌握度 ").append(point.getOverallMasteryRate()).append("%");
                }
                if (point.getAccuracyRate() != null) {
                    sb.append(" · 正确率 ").append(point.getAccuracyRate()).append("%");
                }
                if (point.getCompletedQuestionCount() != null && point.getCompletedQuestionCount() > 0) {
                    sb.append(" · 答题 ").append(point.getCompletedQuestionCount()).append(" 道");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!achievements.isEmpty()) {
            sb.append("## 学习成就\n\n");
            for (LearningAchievementDto item : achievements) {
                sb.append("- ").append(item.getTitle());
                if (item.getTarget() != null) {
                    sb.append("：").append(item.getProgress() != null ? item.getProgress() : 0)
                            .append("/").append(item.getTarget());
                }
                sb.append(item.isAchieved() ? "（已达成）" : "（进行中）").append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private int averageMastery(ProfileDetailDto detail) {
        if (detail.getKnowledgeMap() == null || detail.getKnowledgeMap().isEmpty()) {
            return 0;
        }
        return (int) Math.round(detail.getKnowledgeMap().stream()
                .mapToInt(ProfileDetailDto.KnowledgeMapItem::getMastery)
                .average()
                .orElse(0));
    }
}
