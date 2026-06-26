package com.lqragent.backend.agents.learnerprofile.service;

import com.lqragent.backend.agents.learnerprofile.dto.LearningAchievementDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileDetailDto;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileTrendPointDto;
import com.lqragent.backend.agents.path.entity.LearningPath;
import com.lqragent.backend.agents.path.entity.LearningPathStep;
import com.lqragent.backend.agents.path.repository.LearningPathRepository;
import com.lqragent.backend.agents.path.repository.LearningPathStepRepository;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import com.lqragent.backend.uploadqueue.repository.KbUploadTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileAnalyticsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final LearnerProfileService profileService;
    private final QuizRecordRepository quizRecordRepository;
    private final StudyBehaviorRepository studyBehaviorRepository;
    private final KbUploadTaskRepository uploadTaskRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathStepRepository learningPathStepRepository;

    @Transactional(readOnly = true)
    public List<ProfileTrendPointDto> getTrends(Long userId, String range, String metric) {
        int days = parseRangeDays(range);
        LocalDate start = LocalDate.now().minusDays(Math.max(days - 1, 0));
        ProfileDetailDto detail = profileService.getDetail(userId);
        int avgMastery = averageMastery(detail);

        List<QuizRecord> records = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().toLocalDate().isBefore(start))
                .sorted(Comparator.comparing(QuizRecord::getCreatedAt))
                .toList();

        Map<LocalDate, List<QuizRecord>> byDate = records.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

        List<ProfileTrendPointDto> points = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            List<QuizRecord> dayRecords = byDate.getOrDefault(date, List.of());
            long correct = dayRecords.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            Integer accuracy = dayRecords.isEmpty()
                    ? null
                    : (int) Math.round(correct * 100.0 / dayRecords.size());

            ProfileTrendPointDto.ProfileTrendPointDtoBuilder builder = ProfileTrendPointDto.builder()
                    .date(date.format(DATE_FMT))
                    .completedQuestionCount(dayRecords.size());

            switch (metric == null ? "mastery" : metric) {
                case "accuracy" -> builder.accuracyRate(accuracy);
                case "duration" -> builder.learningDurationMinutes(dayRecords.size() * 5);
                case "questions" -> builder.completedQuestionCount(dayRecords.size());
                case "nodes" -> builder.completedNodeCount(countCompletedStepsOn(userId, date));
                default -> {
                    builder.overallMasteryRate(avgMastery);
                    builder.accuracyRate(accuracy);
                }
            }
            points.add(builder.build());
        }

        if (points.stream().allMatch(p -> (p.getCompletedQuestionCount() == null || p.getCompletedQuestionCount() == 0)
                && p.getAccuracyRate() == null)) {
            return List.of(ProfileTrendPointDto.builder()
                    .date(LocalDate.now().format(DATE_FMT))
                    .overallMasteryRate(avgMastery)
                    .completedQuestionCount(0)
                    .build());
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<LearningAchievementDto> getAchievements(Long userId) {
        ProfileDetailDto detail = profileService.getDetail(userId);
        long quizTotal = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        int uploadCount = (int) uploadTaskRepository.countByUserId(userId);
        PathProgress pathProgress = resolvePathProgress(userId);
        int streak = Math.max(detail.getStreakDays(), estimateStreakDays(userId));

        List<LearningAchievementDto> items = new ArrayList<>();

        items.add(LearningAchievementDto.builder()
                .id("streak")
                .title("坚持学习")
                .description("连续学习 " + streak + " 天")
                .achieved(streak >= 3)
                .progress(Math.min(streak, 7))
                .target(7)
                .level(streak >= 7 ? "gold" : "bronze")
                .build());

        items.add(LearningAchievementDto.builder()
                .id("quiz")
                .title("练习积累")
                .description("累计完成 " + quizTotal + " 道练习")
                .achieved(quizTotal >= 5)
                .progress((int) Math.min(quizTotal, 20))
                .target(20)
                .level(quizTotal >= 20 ? "gold" : quizTotal >= 10 ? "silver" : "bronze")
                .build());

        items.add(LearningAchievementDto.builder()
                .id("upload")
                .title("资料沉淀")
                .description("上传 " + uploadCount + " 份学习资料")
                .achieved(uploadCount >= 1)
                .progress(Math.min(uploadCount, 5))
                .target(5)
                .level(uploadCount >= 5 ? "silver" : "bronze")
                .build());

        items.add(LearningAchievementDto.builder()
                .id("mastery")
                .title("知识进阶")
                .description("已掌握 " + pathProgress.completed + " 个路径节点")
                .achieved(pathProgress.completed >= 3)
                .progress(pathProgress.completed)
                .target(Math.max(pathProgress.total, 10))
                .level(pathProgress.completed >= 5 ? "gold" : "bronze")
                .build());

        return items;
    }

    private int parseRangeDays(String range) {
        if ("7d".equalsIgnoreCase(range)) return 7;
        if ("90d".equalsIgnoreCase(range)) return 90;
        if ("all".equalsIgnoreCase(range)) return 365;
        return 30;
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

    private int countCompletedStepsOn(Long userId, LocalDate date) {
        LearningPath path = learningPathRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE")
                .orElse(null);
        if (path == null) return 0;
        return (int) learningPathStepRepository.findByPathIdOrderByStepOrder(path.getId()).stream()
                .filter(step -> Boolean.TRUE.equals(step.getCompleted())
                        && step.getCompletedAt() != null
                        && step.getCompletedAt().toLocalDate().equals(date))
                .count();
    }

    private PathProgress resolvePathProgress(Long userId) {
        LearningPath path = learningPathRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE")
                .orElse(null);
        if (path == null) {
            return new PathProgress(0, 0);
        }
        List<LearningPathStep> steps = learningPathStepRepository.findByPathIdOrderByStepOrder(path.getId());
        int total = steps.size();
        int completed = (int) steps.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count();
        return new PathProgress(completed, total);
    }

    private int estimateStreakDays(Long userId) {
        List<StudyBehavior> behaviors = studyBehaviorRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (behaviors.isEmpty()) return 0;

        List<LocalDate> dates = behaviors.stream()
                .map(StudyBehavior::getCreatedAt)
                .filter(d -> d != null)
                .map(LocalDateTime::toLocalDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        int streak = 0;
        LocalDate cursor = LocalDate.now();
        for (LocalDate date : dates) {
            if (date.equals(cursor) || date.equals(cursor.minusDays(1))) {
                streak++;
                cursor = date.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    private record PathProgress(int completed, int total) {}
}
