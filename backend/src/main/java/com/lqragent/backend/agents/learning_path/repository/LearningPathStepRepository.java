package com.lqragent.backend.agents.learning_path.repository;

import com.lqragent.backend.agents.learning_path.entity.LearningPathStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningPathStepRepository extends JpaRepository<LearningPathStep, Long> {

    List<LearningPathStep> findByPathIdOrderByStepOrder(Long pathId);
}
