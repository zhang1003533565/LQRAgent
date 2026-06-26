package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.QuizQuestionFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionFavoriteRepository extends JpaRepository<QuizQuestionFavorite, Long> {

    List<QuizQuestionFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<QuizQuestionFavorite> findByUserIdAndQuestionId(Long userId, Long questionId);

    void deleteByUserIdAndQuestionId(Long userId, Long questionId);
}
