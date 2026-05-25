package com.lqragent.backend.quiz.repository;

import com.lqragent.backend.quiz.entity.StudyBehavior;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyBehaviorRepository extends JpaRepository<StudyBehavior, Long> {

    List<StudyBehavior> findByUserIdOrderByCreatedAtDesc(Long userId);
}
