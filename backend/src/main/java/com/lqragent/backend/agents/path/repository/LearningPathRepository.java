package com.lqragent.backend.agents.path.repository;

import com.lqragent.backend.agents.path.entity.LearningPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    List<LearningPath> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<LearningPath> findTopByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
