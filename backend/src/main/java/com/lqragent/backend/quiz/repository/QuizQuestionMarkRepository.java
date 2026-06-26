package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.QuizQuestionMark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionMarkRepository extends JpaRepository<QuizQuestionMark, Long> {

    List<QuizQuestionMark> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<QuizQuestionMark> findByUserIdAndQuestionId(Long userId, Long questionId);

    void deleteByUserIdAndQuestionId(Long userId, Long questionId);
}
