package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.QuizPracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizPracticeSessionRepository extends JpaRepository<QuizPracticeSession, String> {

    Optional<QuizPracticeSession> findByIdAndUserId(String id, Long userId);

    List<QuizPracticeSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
