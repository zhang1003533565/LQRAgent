package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.QuizRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRecordRepository extends JpaRepository<QuizRecord, Long> {

    List<QuizRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<QuizRecord> findByUserIdAndKpId(Long userId, String kpId);

    List<QuizRecord> findByUserIdAndQuestionIdOrderByCreatedAtDesc(Long userId, Long questionId);

    long countByUserIdAndIsCorrect(Long userId, Boolean isCorrect);
}
